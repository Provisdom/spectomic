(def project 'provisdom/spectomic)
(def version "0.3.0")

(set-env! :resource-paths #{"src"}
          :source-paths #{"test"}
          :dependencies '[[adzerk/boot-test "1.2.0" :scope "test"]
                          [adzerk/bootlaces "0.1.13" :scope "test"]
                          [org.clojure/test.check "0.9.0" :scope "test"]

                          [org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
                          [org.clojure/spec.alpha "0.1.123"]])

(require '[adzerk.boot-test :refer [test]]
         '[adzerk.bootlaces :refer :all])

(bootlaces! version)

(task-options!
  pom {:project     project
       :version     version
       :description "FIXME: write description"
       :url         "http://example/FIXME"
       :scm         {:url "https://github.com/yourname/spectomic"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask dev-env
         []
         (set-env!
           :repositories #(conj %
                                ["datomic" {:url      "https://my.datomic.com/repo"
                                            :username (System/getenv "DATOMIC_USERNAME")
                                            :password (System/getenv "DATOMIC_PASSWORD")}])
           :dependencies #(conj % '[com.datomic/datomic-pro "0.9.5561"]))
         identity)

(replace-task!
  [r repl]
  (fn [& xs]
    (dev-env)
    (apply r xs)))

(when-let [v (resolve 'lein-generate)]
  (alter-var-root v
                  (fn [g]
                    (fn [& xs] (dev-env) (apply g xs)))))

(deftask deploy
         []
         (comp (build-jar) (push-release)))