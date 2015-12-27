(ns tweetshovel.core
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [twitter.oauth :refer [make-oauth-creds]]
            [twitter.api.restful :as twr]
            [clojure.tools.cli :refer [parse-opts]]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.core :as append])
  (:gen-class))

(defn- sleep-time [response]
  "Computes the time required to sleep before the rate limit resets. If the
  current rate limit remaining is greater than 1, this is zero. Otherwise, it's
  the amount of time before the rate limit resets.

  `response` The response map from a Twitter API call.

  Returns 0 if there is more than one API call remaining, otherwise returns
  the number of seconds before the rate limit resets.
  "
  (let [{{:keys [x-rate-limit-reset x-rate-limit-remaining]} :headers} response
        reset (read-string x-rate-limit-reset)
        remaining (read-string x-rate-limit-remaining)]
    (if (< remaining 2)
      (-> (System/currentTimeMillis)
        (/ 1000)  ; Convert to epoch seconds.
        int       ; Convert from rational to integer.
        (- reset) ; Subtract the epoch reset time.
        -         ; Flip the sign.
        (+ 30)    ; Add buffer.
        (* 1000)) ; Convert to milliseconds.
      0)))

(defn- twitter-request
  "Makes the specified request, trying up to three times if necessary. Each try
  sleeps for one second times the number of tries.

  `req-fn` The function making the request to Twitter's API.

  `req-args` The arguments to `req-fn`.

  `err-count` The number of errors encountered for this call. Starts at zero,
  incrementing with successive recursions.

  Returns the response to the request as soon as a complete request is made,
  throws an exception otherwise.
  "
  ;; This is the entry form, with an error count of zero.
  ([req-fn req-args]
    (twitter-request req-fn req-args 0))
  ;; This form has tracks the error count.
  ([req-fn req-args err-count]
    (try
      (req-fn req-args)
      (catch Exception e
        (let [updated-err (inc err-count)]
          (Thread/sleep (* 1000 updated-err))
          (if (< err-count 3)
            (do
              (timbre/warn "Error with API call. Retrying.")
              (twitter-request req-fn req-args updated-err))
            (do
              (timbre/error "Too many errors with API call.")
              (throw e))))))))

(defn- shovel
  "Shovels tweets from Twitter's API, paging through results and respecting
  the rate limits.

  `shovel-fn` The function to shovel. Takes a map of parameters passed to the
  function.

  `extract` A function that takes the response and returns the tweets.

  `terminate?` Called on the total tweets (new and old) and returns true when
  the shoveling should stop.

  `next-args` A function that takes all of the tweets (new and old) and returns
  new parameters for the next API call.

  `tweets` The tweets retrieved by this function.

  `shovel-args` Argument map passed to `shovel-fn`.
  "
  ([shovel-fn extract terminate? next-args shovel-args]
    (shovel shovel-fn extract terminate? next-args [] shovel-args))

  ([shovel-fn extract terminate? next-args tweets shovel-args]
    (timbre/info (str "Making API call with params: " shovel-args "."))
    (let [response (twitter-request shovel-fn shovel-args)
          new-tweets (extract response)
          to-sleep (sleep-time response)]
      ;; Sleeps for 0 seconds if not near the rate limit, or sleeps until the
      ;; rate limit resets.
      (when (> to-sleep 0)
          (timbre/info (str "Rate limit reached. Sleeping for " to-sleep ".")))
      (Thread/sleep to-sleep)
      (let [all-tweets (into tweets new-tweets)]
        (if (and (> (count new-tweets) 0)
                 (not (terminate? all-tweets)))
          (recur
            shovel-fn
            extract
            terminate?
            next-args
            all-tweets
            (into shovel-args (next-args all-tweets)))
          all-tweets)))))

(defn make-creds
  "Creates the Twitter API authentication from a map of access tokens.

  `cred-map` is a map with the following keys/values:
  `:CONSUMER_KEY`, `:CONSUMER_SECRET`, `:OAUTH_TOKEN`, and `:OAUTH_SECRET`,
  for the public and private user and oauth credentials, respectively.

  Returns the map containing the credentials required to access the Twitter API.
  "
  [cred-map]
  (make-oauth-creds (:CONSUMER_KEY cred-map)
                    (:CONSUMER_SECRET cred-map)
                    (:OAUTH_TOKEN cred-map)
                    (:OAUTH_SECRET cred-map)))

(defn shovel-timeline
  "Shovels tweets from a user's timeline.

  `screen-name` is the Twitter screen name of the user.

  `creds` is the authentication map, returned by [[make-creds]].

  `params` are any parameters to the API call as a map. The parameter names are
  keywords corresponding directly with the API parameters. See more about the
  available API parameters for this call at the Twitter API documentation
  [here](https://dev.twitter.com/rest/reference/get/statuses/user_timeline).
  Any dashes in the parameter name keywords get converted to underscores. A
  default value of `:count` is provided, which can be overridden by placing a
  new value in `params`. Be aware that the screen name parameter `:screen-name`
  *will* override the provided `screen-name` argument if present in this map.

  `terminate?` is a function called after each individual API call is made on
  the set of *all* tweets that have been retrieved thus far, including the
  new ones. If not provided, `terminate?` always returns `false`, meaning
  the complete user timeline will be retrieved.

  Returns a vector of tweets.
  "
  ([screen-name creds]
    (shovel-timeline screen-name creds {}))
  ;; Default terminator that returns false (gets all tweets).
  ([screen-name creds params]
    (shovel-timeline screen-name creds params (fn [x] false)))
  ;; Terminator is specified explicitly.
  ([screen-name creds params terminate?]
    (shovel
      #(twr/statuses-user-timeline :oauth-creds creds :params %)
      #(:body %) ; extract
      terminate?
      (fn [t] {:max-id (dec (apply min (map :id t)))}) ; next-args
      (into {:count 200 :screen-name screen-name} params))))

(defn shovel-search
  "Shovels tweets from the search API.

  `query` is a query string to be applied to the search. See the Twitter API
  [documentation](https://dev.twitter.com/rest/reference/get/search/tweets)
  for details on this string.

  `creds` is the authentication map, returned by [[make-creds]].

  `params` are any parameters to the API call as a map. The parameter names are
  keywords corresponding directly with the API parameters. See more about
  API parameters for this call at the Twitter API documentation
  [here](https://dev.twitter.com/rest/reference/get/search/tweets).
  Any dashes in parameter name keywords get converted to underscores.
  A default value of `:count` is provided, which can be overridden by placing a
  new value in `params`. Be aware that the query parameter `:q` *will* override
  the provided `query` argument if present in this map.

  `terminate?` is a function called after each individual API call is made on
  the set of *all* tweets that have been retrieved thus far, including the new
  ones. The `terminate?` for the command line tool always returns 'false', so use
  caution with this option when running from the shell.

  Returns a vector of tweets."
  ([query creds]
    (shovel-search query creds {}))
  ;; Default terminator stops after 1600 tweets.
  ([query creds params]
    (shovel-search query creds params (fn [x] false)))
  ;; Terminator is specified explicitly.
  ([query creds params terminate?]
    (shovel
      #(twr/search-tweets :oauth-creds creds :params %)
      #(:statuses (:body %)) ; extract
      terminate?
      (fn [t] {:max-id (dec (apply min (map :id t)))})
      (into {:count 100 :q query} params))))

(defn -main
  "Shovels tweets from a specified timeline or search query by paging through
  multiple calls, all while respecting Twitter's rate limits.

  `-t --timeline SCREEN_NAME` Shovels the user's timeline

  `-s --search QUERY` Shovels the search query. Use with caution, as this option
  could result in very long scrapes. Use `--limit` to avoid this.

  Only one of the above options should be selected.

  `-a --auth AUTH_FILE` A JSON file with the authorization tokens. The JSON file
  must be a single object with the following keys: \"CONSUMER_KEY\",
  \"CONSUMER_SECRET\", \"OAUTH_TOKEN\", \"OAUTH_SECRET\". This argument is
  required.

  `-o --output FILE` The name of the output file to output the tweets. The
  output format is JSON. This option is not required, and defaults to STDOUT.
  
  `-l --limit LIMIT` An approximate limit on the number of tweets. This option
  is not required. If it isn't excluded, all tweets (timeline or search) will be
  pulled. It's recommended this be used with `--search` to limit the length of
  the scrapes.
  
  `-v --verbose` Activates logging to STDERR. API calls, errors, and sleeps
  are logged. By default logging is off."
  [& args]
  (let [options (parse-opts
    args
    [["-t" "--timeline SCREEN_NAME" "Shovel the user's timeline." :id :timeline]
     ["-s" "--search QUERY" "Shovel the search query." :id :search]
     ["-a" "--auth AUTH_FILE" "Specify the JSON authentication file." :id :auth]
     ["-o" "--output FILE" "The output file for the tweets."
        :default (System/out) :default-desc "STDOUT" :id :output]
     ["-l" "--limit LIMIT" "Approximate limit on the number of tweets." :id :limit
        :parse-fn #(Integer/parseInt %)]
     ["-v" "--verbose" "Activates logging to STDERR." :id :verbose]
     ["-h" "--help" "Displays help." :id :help]])]

    ;; Verify that there are no errors in the option set.
    (when (:errors options)
      (println (str args " is an invalid argument string."))
      (println (:summary options)) (System/exit 1))

    ;; Display help if requested.
    (when (:help (:options options))
      (println (:summary options)) (System/exit 0))

    ;; Verify that authentication JSON file has been specified.
    (when (nil? (:auth (:options options)))
      (println "An authentication file must be specified.")
      (println (:summary options)) (System/exit 1))
    
    ;; Set up logging. DEBUG until configured.
    (timbre/set-config!
      (into timbre/example-config
        {:appenders
          {:err-appender
           (into
              (timbre/println-appender {:stream :*err*})
              ;; Turns off the appender if --verbose wasn't used.
              {:enabled? (:verbose (:options options))})}}))

    ;; Execute the shoveler.
    (let [{{:keys [output auth]} :options} options
          creds (make-creds (json/parse-string (slurp auth) true))
          terminate? (if-let [limit (:limit (:options options))]
                             (fn [x] (>= (count x) limit))
                             (fn [x] false))]
      
      (spit output (json/generate-string
        (cond
          (:timeline (:options options))
          (shovel-timeline (:timeline (:options options))
                           creds
                           {}
                           terminate?)

          (:search (:options options))
          (shovel-search (:search (:options options))
                         creds
                         {}
                         terminate?)

          :else (do (println "Must specify something to shovel.")
                (println (:summary options))
                (System/exit 1)))
        {:pretty true})))))
