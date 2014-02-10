(ns yesql.named-parameters
  (:require [clojure.java.io :refer [resource]]
            [instaparse.core :as instaparse]
            [yesql.util :refer [str-all]]))

(def parser
  (instaparse/parser (resource "yesql/query.bnf")))

(defn split-at-parameters
  [query]
  (->> (parser query :start :statement)
       (instaparse/transform {:statement vector
                              :substatement str-all
                              :string str-all
                              :string-delimiter identity
                              :string-normal identity
                              :string-special str-all
                              :parameter identity
                              :placeholder-parameter symbol
                              :named-parameter symbol})))

(defn- args-to-placehoders
  [args]
  (if-not (coll? args)
    "?"
    (clojure.string/join "," (repeat (count args) "?"))))

(defn reassemble-query
  "Given a query that's been split into text-and-symbols, and some arguments, reassemble
it as the pair [string-with-?-parameters, args], suitable for supply to clojure.java.jdbc."
  [split-query args]
  (assert (= (count (filter symbol? split-query))
             (count args))
          "Query parameter count must match args count.")
  (loop [query-string ""
         final-args []
         [query-head & query-tail] split-query
         [args-head & args-tail :as remaining-args] args]
    (cond
     (nil? query-head) (vec (cons query-string final-args))
     (string? query-head) (recur (str query-string query-head)
                                 final-args
                                 query-tail
                                 remaining-args)
     (symbol? query-head) (recur (str query-string (args-to-placehoders args-head))
                                 (if (coll? args-head)
                                   (apply conj final-args args-head)
                                   (conj final-args args-head))
                                 query-tail
                                 args-tail))))
