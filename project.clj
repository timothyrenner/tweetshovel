(defproject tweetshovel "0.1.0"
  :description "A library for scraping Tweets from Twitter's REST API."
  :url "https://github.com/timothyrenner/tweetshovel"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [twitter-api "0.7.8"]
                 [cheshire "5.5.0"]
                 [org.slf4j/slf4j-log4j12 "1.6.4"]
                 [com.taoensso/timbre "4.1.4"]
                 [org.clojure/tools.cli "0.3.1"]]
  :plugins [[codox "0.8.10"]
            [lein-bin "0.3.5"]]
  :codox {:sources ["src"]
          :defaults {:doc/format :markdown}}
  :bin {:name "tweetshovel"}
  :main ^:skip-aot tweetshovel.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
