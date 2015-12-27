(ns tweetshovel.core-test
  (:require [clojure.test :refer :all]
            [tweetshovel.core :refer :all]
            [twitter.oauth :as oauth]))

;;;; Some black magic to allow testing of private functions.
;;;; Credit to Stuart Sierra.
;;;; https://groups.google.com/forum/#!topic/clojure/ODXlQFq5MPY
(defn refer-private [ns]
  (doseq [[symbol var] (ns-interns ns)]
    (when (:private (meta var))
      (intern *ns* symbol var))))

(refer-private 'tweetshovel.core)

;;;; TESTS

(deftest make-creds-test
  (testing "Creates the correct map."
    (is (= (oauth/make-oauth-creds "a" "b" "c" "d")
           (make-creds {:CONSUMER_KEY    "a"
                        :CONSUMER_SECRET "b"
                        :OAUTH_TOKEN     "c"
                        :OAUTH_SECRET    "d"})))))

(deftest sleep-time-test
  (testing "Remaining greater than 2."
    (is (= 0 (sleep-time
      {:headers
        {:x-rate-limit-reset (str 0) :x-rate-limit-remaining (str 3)}}))))
  (testing "Remaining less than 2."
    ;; If the result is greater than 35000 (35 seconds) it's a pass - greater
    ;; than the time needed to sleep. The actual result will vary from system
    ;; to system so it's a little loose. It could be tighter, I suppose.
    (is (<= 35000 (sleep-time
      {:headers
        {:x-rate-limit-reset (str (+ 5 (/ (System/currentTimeMillis) 1000)))
         :x-rate-limit-remaining (str 1)}})))))

(deftest twitter-request-test
  (testing "Exception thrown with consecutive errors."
    (is (thrown? Exception (twitter-request (fn [x] (throw Exception e)) []))))
  (testing "Good  call."
    (is (= "Good call!" (twitter-request (fn [x] "Good call!") [])))))

(deftest shovel-test
  (testing "Proper call with default terminator with no sleep."
    ;; The shovel function returns an empty body, and should cause an immediate
    ;; termination of the recursion.
    (= [] (shovel
      (fn [x] {:headers
        {:x-rate-limit-remaining "180"
          :x-rate-limit-reset (str (int (/ (System/currentTimeMillis) 1000)))}
          :body []}) ; shovel-fn
      (fn [x] (:body x)) ; extract
      (fn [x] true) ; terminate ?
      (fn [x] {}) ; next-args
      {}))) ; shovel-args
  (testing "Proper call with sleep."
    ;; This shovel function returns only two tweets.
    (let [ten-seconds (-> (System/currentTimeMillis) (/ 1000) int (+ 10) str)
          first-response {:headers {:x-rate-limit-remaining "2"
                                    :x-rate-limit-reset ten-seconds}
                          :body [{:id 2}]}
          second-response {:headers {:x-rate-limit-remaining "1"
                                     :x-rate-limit-reset ten-seconds}
                           :body [{:id 1 :text "Hello"}]}]
      (= [{:id 2} {:text "Hello" :id 1}]
         (shovel
          (fn [p] (if (nil? (:max-id p))
                      first-response
                      second-response)) ; shovel-fn
          (fn [x] (:body x)) ; extract
          (fn [x] (>= (count x) 2)) ; terminate?
          (fn [x] {:max-id (dec (apply min (map :id x)))}) ; next-args
          {}))))) ; shovel-args
