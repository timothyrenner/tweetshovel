
![Clojars Project](http://clojars.org/tweetshovel/latest-version.svg)
###[API Documentation](http://timothyrenner.github.io/tweetshovel/) 

# Tweetshovel

Tweetshovel is a command line tool for accessing Twitter's REST APIs with an explicit design focus on scraping.
It strictly obeys Twitter's rate limits (i.e. _without_ actually hitting it and getting a 429), and automatically pages through the tweet timelines or other results as needed.
If your call gets too close to the rate limit, tweetshovel will sleep until the rate limit resets.

It is also a Clojure library that can be used for enhanced control over things like authentication, shovel termination, and output.


## Reasoning

This project came about as a "productized" version of some tooling I've been iterating on for a while.
I do a lot of exploring of small Twitter datasets for things like NLP or graph relations, and I wanted a tool that could grab a user account or search and dump it to a JSON file with as little fuss as possible.

There's already tooling that does some of this.
The excellent [Python Twitter Tools](http://mike.verdone.ca/twitter/) package has an archiver for the REST APIs.
However, from what I can tell this functionality isn't _proactive_ about the rate limit.
Under normal use cases - one or two (or even ten) account scrapes - this isn't a problem.
When you need 500 and you're doing it every night that's a different situation.
Tweetshovel leaves one call available before it sleeps so that if it's run twice in a row, it can obtain the number of calls remaining prior to sleeping.

### Tweetshovel is good for:

* Small, focused datasets.
* Quickly yanking a search or user timeline.

### Tweetshovel is not good for:

* Large datasets.
* Streaming.
* Building client applications.

### Why the REST API?

Normally when people think of data analysis on tweets, they think of the streaming API.
Streaming is the best way to get a lot of data, but connections can drop, computers can crash, etc, which can cause problems if completeness is an issue.
For example if I want all tweets members of Congress made during the State of the Union, I'd need an active streaming connection to all of their handles for the duration of the speech.
That connection would need to be active and good the whole time or the data collection is hosed.
I'd only get one shot.
With tweetshovel, I can get them whenever I want - even if it does take a couple of hours.

The REST API is also the only way to access tweets made in the past.
For example if I wanted to count the number of times [Ted Nugent](https://twitter.com/TedNugent) made a grammatical error in the last few months, I'd have no choice but to use the REST API.

These are the kinds of things tweetshovel was built for.

The above cases focus on user timelines, which is the primary focus of tweetshovel's design.
It can also scrape the search API, but your mileage may vary.
The search API itself is not an exhaustive search capability, and is not designed to provide a complete dataset of tweets matching a query.
You can read more about Twitter's search API [here](https://dev.twitter.com/rest/public/search).

I will say that for small datasets it works pretty well.

## Command Line Usage

The command line tool is a standalone executable.
It requires a recent version of Java (1.6+ I think).
Grab it <a href="https://s3.amazonaws.com/timothyrenner.binaries/tweetshovel" download>here</a>.
Be sure to give it execute permissions.

Running it is as simple as a command line execution:

```
$ ./tweetshovel [OPTIONS]
```

Where `OPTIONS` are as follows:

```
-t, --timeline SCREEN_NAME          Shovel the user's timeline.
-s, --search QUERY                  Shovel the search query.
-a, --auth AUTH_FILE                Specify the JSON authentication file.
-o, --output FILE           STDOUT  The output file for the tweets.
-l, --limit LIMIT                   Approximate limit on the number of tweets.
-h, --help                          Displays help.

```

The `--auth` option needs to point to a file with the authentication.
That file should be a a JSON file that looks like this:
```json
{
  "CONSUMER_KEY":    "xxxxxxxxxx",
  "CONSUMER_SECRET": "xxxxxxxxxx",
  "OAUTH_TOKEN":     "xxxxxxxxxx",
  "OAUTH_SECRET":    "xxxxxxxxxx"
}
```
With the `xxxxxxxxxx` filled in with the Twitter API credentials.
You can read more about Twitter's credentials and getting set up [here](https://apps.twitter.com).
Basically, you have to register an "app", and you get the tokens after doing a little OAuth dancing.
The `"OAUTH_TOKEN"` and `"OAUTH_SECRET"` fields are also sometimes referred to as access token and access secret.

## Library Usage

To use as a Clojure library, include in your Leiningen `project.clj`'s dependencies:

```clojure
[tweetshovel "0.1.0"]
```

In your namespace, include with

```clojure
(ns my.ns
  (:require [tweetshovel.core]))
```

Presently, the `tweetshovel` namespace exposes three functions (excluding `-main`):

`shovel-timeline` takes the screen name of the user to shovel tweets from, the credentials (as created by `make-creds`, described below), a parameter map for the API call, and an optional terminator function.
The parameter map is a keyword-ized map of the options available with the [`statuses/user_timeline`](https://dev.twitter.com/rest/reference/get/statuses/user_timeline) endpoint.
Any dashes in the keywords of the parameter map get converted to underscores.
The terminator operates on all tweets obtained so far in the shovel and returns `true` when it's time to stop shoveling.
For example, a terminator that looks like this:

```clojure
(fn [x] (> (count x) 2000))
```

will terminate after more than 2000 tweets have been obtained.
The terminator can be any function that takes a vector of tweets, and defaults to always returning `false`.
Terminators are useful for prolonged scrapes of large numbers of users, where the rate limit sleeping will result in a substantial delay.
The terminator allows fine-grained control over the number of calls, restricting them to the bare minimum required.
Note that the terminator is not designed to control the output - just the calls.
You can always apply a `filter` or something after the calls are made to get exactly what you want.

`shovel-search` works exactly like `shovel-timeline`, except instead of a screen name it takes a search query for the [`search/tweets`](https://dev.twitter.com/rest/reference/get/search/tweets) endpoint.

`make-creds` reads a map with the authentication tokens identical to the JSON object in the command line auth, except with keywords: `:CONSUMER_KEY`, `:CONSUMER_SECRET`, `:OAUTH_TOKEN`, and `:OAUTH_SECRET`.
It's basically a pass-through for the authentication for the API client tweetshovel uses.

The API docs are hosted [here](http://timothyrenner.github.io/tweetshovel/).

### Examples

```clojure
;; Grabs all available tweets from Kanye West, trimming the user information.
(shovel-timeline "kanyewest" (make-creds cred-map) {:trim-user "true"})

;; Grabs the latest 500(ish) tweets from the President of the US.
(shovel-timeline "BarackObama"
                 (make-creds cred-map)
                 {}
                 #(>= (count %) 500))

;; Searches for tweets in English related to Clojure.
(shovel-search "#clojure"
               (make-creds cred-map)
               {:lang "en"}
               #(>= (count %) 1000))
```

## Building

To build from source, you'll need [Leiningen](http://leiningen.org/).
Run

```
lein uberjar
```

to build the executable jar bundled with the dependencies.

Use

```
lein bin
```

to build the standalone executable.

Run

```
lein test
```

to run the unit tests.


## Core Libraries

There are two core libraries that tweetshovel leans on:
* adamwynne's [twitter-api](https://github.com/adamwynne/twitter-api) for Twitter API access.
* dakrone's [cheshire](https://github.com/dakrone/cheshire) for JSON parsing and printing.

## Roadmap

There are several things that I'd like to implement on top of this initial release.
In no particular order:

* Tweet "hydration" with `statuses/lookup`. `twitter-api` doesn't have a function for this API method, so I'll need to add that and PR it. I'm not sure how long that will take me.
* Status printing to screen. It would be nice to print the rate status and sleep times to the screen as an option (like `--verbose`). I haven't worked out the best way to do this so I'm open to suggestions.
* Followers / following shovel functions. This would make for some fun graph-based analyses.
* Python implementation. I'd like to adapt this into a Python library for tighter integration with the Python data science stack.
* Speed improvements. Tweetshovel's pretty slow. It's not a huge deal because there really isn't much need for high performance, but it could definitely be faster.

## License

Copyright © 2015 Timothy Renner

Distributed under the Eclipse Public License, same as Clojure.
