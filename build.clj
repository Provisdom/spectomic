(ns build
  (:require
    [clojure.tools.build.api :as b]
    [org.corfield.build :as bb]))

(def lib 'provisdom/spectomic)
(def version (format "1.0.%s" (b/git-count-revs nil)))

(defn jar "Build lib jar." [opts]
  (-> (assoc opts :lib lib :version version)
    (bb/clean)
    (bb/jar))
  opts)

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
    (assoc :lib lib :version version)
    (bb/deploy)))
