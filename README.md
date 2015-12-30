
###[API Documentation (Development)](http://timothyrenner.github.io/tweetshovel/) 

# Tweetshovel

Tweetshovel is a command line tool for accessing Twitter's REST APIs with an explicit design focus on scraping.
It strictly obeys Twitter's rate limits (i.e. _without_ actually hitting it and getting a 429), and automatically pages through the tweet timelines or other results as needed.
If your call gets too close to the rate limit, tweetshovel will sleep until the rate limit resets.

~~It is also a Clojure library that can be used for enhanced control over things like authentication, shovel termination, and output.~~
The library functionality has been removed to allow breaking backend changes needed to perform long scrapes from the command line.
Those changes are presently in development.

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
-v, --verbose                       Activates logging to STDERR.
-p, --params                None.   Additional parameters for the API calls.
                                    <p1_name>=<p1_val>,<p2_name>=<p2_val>,..
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
* ~~Status printing to screen. It would be nice to print the rate status and sleep times to the screen as an option (like `--verbose`). I haven't worked out the best way to do this so I'm open to suggestions.~~ Implemented in master.
* Followers / following shovel functions. This would make for some fun graph-based analyses.
* Python implementation. I'd like to adapt this into a Python library for tighter integration with the Python data science stack.
* Speed improvements. Tweetshovel's pretty slow. It's not a huge deal because there really isn't much need for high performance, but it could definitely be faster.

## License

Copyright © 2015 Timothy Renner

Distributed under the Eclipse Public License, same as Clojure.
