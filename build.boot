(def project 'provisdom/spectomic)
(def version "0.1-alpha1")

(set-env! :resource-paths #{"src"}
          :source-paths #{"test"}
          :dependencies '[[adzerk/boot-test "1.2.0" :scope "test"]
                          [org.clojure/test.check "0.9.0" :scope "test"]

                          [org.clojure/clojure "1.9.0-alpha15"]])

(require '[adzerk.boot-test :refer [test]])

(task-options!
  pom {:project     project
       :version     version
       :description "FIXME: write description"
       :url         "http://example/FIXME"
       :scm         {:url "https://github.com/yourname/spectomic"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})

(defn dev-env!
  []
  (set-env!
    :repositories #(conj %
                         ["datomic" {:url      "https://my.datomic.com/repo"
                                     :username (System/getenv "DATOMIC_USERNAME")
                                     :password (System/getenv "DATOMIC_PASSWORD")}])
    :dependencies #(conj % '[com.datomic/datomic-pro "0.9.5561"])))

(replace-task!
  [r repl]
  (fn [& xs]
    (dev-env!)
    (apply r xs)))

(when (resolve 'lein-generate)
  (replace-task!
    [g lein-generate]
    (fn [& xs]
      (dev-env!)
      (apply g xs))))