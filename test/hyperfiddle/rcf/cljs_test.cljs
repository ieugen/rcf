(ns hyperfiddle.rcf.cljs-test
  (:require [clojure.core.async :refer [chan >! go go-loop <! timeout close!]]
            [hyperfiddle.rcf :as rcf :refer [tests ! %]]
            [missionary.core :as m]))

(tests
   "equality"
   (inc 1) := 2)

(tests
   "nested tests"
   (tests
    "are sometimes convenient"
    1 := 1))

(tests
   "Macros are supported"
   (with-out-str
     "print is captured"
     (print "hello")
     1 := 1)
   := "hello")

(tests
   "let body"
   (let [a 1, b 2] [a b] := [1 2]))

(tests
   "def in same form (Gilardi scenario)"
   (def a 1)
   a := 1

   "inline def"
   ((def b identity) 1) := 1)


(tests
   ((fn [a] a := 1) 1) := 1)

(tests
   "REPL bindings work"
   (inc 1) := 2
   (dec *1) := 1

   (tests 1 2 3 *3 := 1, *2 := 2, *1 := 3))

(tests
   "wildcards"
   {:a :b, :b [2 :b]} := {:a _, _ [2 _]})

(tests
   "unification"
   {:a :b, :b [2 :b]} := {:a ?b, ?b [2 ?b]})

(tests
   "unification on reference types"
   (def x (atom nil))
   {:a x, :b x} := {:a ?x, :b ?x})

(tests
 (rcf/set-timeout! 100)
 "async tests"
 (tests
  (defn set-timeout [f ms] (js/setTimeout ms f))
  (rcf/! 1) (set-timeout 10 (fn []
                              (rcf/! 2) (set-timeout 200 (fn []
                                                           (rcf/! 3)))))
  % := 1
  % := 2
  % := ::rcf/timeout))

(tests
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
 (close! c))

(tests
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
