# RCF – a REPL-first test macro for Clojure/Script (now with async tests!)

RCF turns your Rich Comment Forms into tests (in the same file as your functions). Send form or file to REPL to run tests and it squirts dopamine ✅✅✅. It's good, try it!

![](https://i.imgur.com/nBOOZq7.png)

Features
* Clojure/Script
* Async tests (new!)
* No file watchers, no extra windows, no beeping, no latency
* Natural REPL workflow
* One key-chord to run tests
* Zero boilerplate

Deeper goal: **a notation for communication**
* Documentation tool. RCF lets you share example usages next to the source code of the function (which is way better than docstrings). Figuring out what dense Clojure code does is actually really hard and RCF fixes that.
* Pair programming tool. While pairing on Zoom, bang out some assertions quickly, right in the file you're working on. Watch your communication bandwidth improve.
* Teaching tool. RCF helps beginners experiment and check their work.

RCF is specifically engineered to support [hyperfiddle/photon, our upcoming reactive dialect of Clojure](https://hyperfiddle.notion.site/Reactive-Clojure-You-don-t-need-a-web-framework-you-need-a-web-language-44b5bfa526be4af282863f34fa1cfffc), that we intend to test, document and teach with RCF.

Hype quotes:
* "I used hyperfiddle/RCF in a recent successful interview. RCF was a massive help in communication and a fast tool for thought whilst under the conditions of technical interview."
* "I currently use this to do leetcode style questions as 'fun practice.' It certainly didn't feel fun before!"
* "I think people make the mistake of comparing this with other methods of inlining tests near their function definitions (which has been possible, though uncommon, for a long time). The integration with the REPL, low syntax/interface, reduces friction and makes testing more attractive as a language of communication and verification."

# Dependency

Project maturity: experimental. The current development priority is great async tests.

```Clojure
{:deps {hyperfiddle/rcf {:git/url "https://github.com/hyperfiddle/rcf.git" :sha ...}}}
```

Breaking changes:
 * 2021 Oct 20: custom reporters now dispatch on qualified keywords, see [#19](https://github.com/hyperfiddle/rcf/issues/19#issuecomment-946757852)

[![JVM](https://github.com/hyperfiddle/rcf/actions/workflows/tests_clj.yml/badge.svg?branch=master)](https://github.com/hyperfiddle/rcf/actions/workflows/tests_clj.yml)
[![NodeJS](https://github.com/hyperfiddle/rcf/actions/workflows/tests_node.yml/badge.svg?branch=master)](https://github.com/hyperfiddle/rcf/actions/workflows/tests_node.yml)
[![Browser](https://github.com/hyperfiddle/rcf/actions/workflows/tests_browser.yml/badge.svg?branch=master)](https://github.com/hyperfiddle/rcf/actions/workflows/tests_browser.yml)

# Usage

`(tests)` blocks erase by default (macroexpanding to nothing), which avoids a startup time performance penalty as well as keeps tests out of prod.

It's an easy one-liner to turn on tests in your entrypoint:

```clojure
(ns user                                     ; user ns is loaded by REPL startup
  (:require [hyperfiddle.rcf]))

(hyperfiddle.rcf/enable!)
```
Tests are run when you send a file or form to your Clojure/Script REPL. In Cursive, that's cmd-shift-L to load the file.

```clojure
(ns example
  (:require [hyperfiddle.rcf :refer [tests ! %]]))

(tests
  "equality"
  (inc 1) := 2

  "wildcards"
  {:a :b, :b [2 :b]} := {:a _, _ [2 _]}

  "unification"
  {:a :b, :b [2 :b]} := {:a ?b, ?b [2 ?b]}

  "unification on reference types"
  (def x (atom nil))
  {:a x, :b x} := {:a ?x, :b ?x}

  "REPL bindings work"
  (inc 1)
  := 2
  (dec *1)
  := 1

  (tests
    "nested tests for convenience"
    1 := 1))
```
```text
Loading src/example.cljc...
✅✅✅✅✅✅✅✅Loaded
```

# Async tests

```Clojure
(ns example
  (:require [clojure.core.async :refer [chan >! go go-loop <! timeout close!]]
            [hyperfiddle.rcf :as rcf :refer [tests ! %]]
            [missionary.core :as m]))

(rcf/set-timeout! 100)

(tests
  "async tests"
  #?(:clj  (tests
             (future
               (rcf/! 1) (Thread/sleep 10)        ; tap value to queue
               (rcf/! 2) (Thread/sleep 200)
               (rcf/! 3))
             % := 1                               ; pop queue
             % := 2
             % := ::rcf/timeout)
     :cljs (tests
             (defn setTimeout [f ms] (js/setTimeout ms f))
             (rcf/! 1) (setTimeout 10 (fn []
             (rcf/! 2) (setTimeout 200 (fn []
             (rcf/! 3)))))
             % := 1
             % := 2
             % := ::rcf/timeout))

  "core.async"
  (def c (chan))
  (go-loop [x (<! c)]
    (when x
      (<! (timeout 10))
      (! x)
      (recur (<! c))))
  (go (>! c :hello) (>! c :world))
  % := :hello
  % := :world
  (close! c)

  "missionary"
  (def !x (atom 0))
  (def dispose ((m/reactor (m/stream! (m/ap (! (inc (m/?< (m/watch !x)))))))
                (fn [_] #_(prn ::done)) #(prn ::crash %)))
  % := 1
  (swap! !x inc)
  (swap! !x inc)
  % := 2
  % := 3
  (dispose))
```

# CI

To run in CI, configure a JVM flag for RCF to generate clojure.test deftests, and then run them with clojure.test. [Github actions example](https://github.com/hyperfiddle/rcf/tree/master/.github/workflows).

```Clojure
; deps.edn
{:aliases {:test {:jvm-opts ["-Dhyperfiddle.rcf.generate-tests=true"]}}}
```
```bash
% clj -M:test -e "(require 'example)(clojure.test/run-tests 'example)"

Testing example
✅✅✅✅✅✅✅✅
Ran 1 tests containing 8 assertions.
0 failures, 0 errors.
{:test 1, :pass 8, :fail 0, :error 0, :type :summary}
```

# ClojureScript configuration

```Clojure
(ns dev-entrypoint
  (:require [example] ; transitive inline tests will erase
            [hyperfiddle.rcf :refer [tests]]))

; wait to enable tests until after app namespaces are loaded
(hyperfiddle.rcf/enable!)

; subsequent REPL interactions will run tests

; prevent test execution during cljs hot code reload
#?(:cljs (defn ^:dev/before-load stop [] (hyperfiddle.rcf/enable!)))
#?(:cljs (defn ^:dev/after-load start [] (hyperfiddle.rcf/enable! false)))
```

# FAQ

*One of my tests threw an exception, but the stack trace is empty?* — you want `{:jvm-opts ["-XX:-OmitStackTraceInFastThrow"]}` [explanation](https://web.archive.org/web/20190416091616/http://yellerapp.com/posts/2015-05-11-clojure-no-stacktrace.html) (this may be JVM specific)

*I see no output* — RCF is off by default, run `(hyperfiddle.rcf/enable!)`

*Emacs has no output and tests are enabled* — check if your emacs supports emojis

*How do I customize what’s printed at the REPL?* — see [reporters.clj](https://github.com/hyperfiddle/rcf/blob/03c821c3875c3dfe647c945430ecdc5a7c8b594f/src/hyperfiddle/rcf/reporters.clj), [reporters.cljs](https://github.com/hyperfiddle/rcf/blob/03c821c3875c3dfe647c945430ecdc5a7c8b594f/src/hyperfiddle/rcf/reporters.cljs)

# Community

&#35;hyperfiddle @ clojurians.net

![Scroll Of Truth meme saying "you do not really understand something until you can explain it as a passing test".](./doc/meme.jpg)
