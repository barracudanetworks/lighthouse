(defproject com.barracuda/lighthouse #=(clojure.string/trim #=(slurp "resources/VERSION"))
  :description "A data-driven Kubernetes pre-processor"
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [borkdude/sci "0.2.5"]
                 [borkdude/edamame "0.0.11"]
                 [borkdude/rewrite-edn "0.0.2"]
                 [camel-snake-kebab "0.4.2"]
                 [cli-matic "0.4.3"]
                 [clj-commons/clj-yaml "0.7.1"]
                 [dorothy "0.0.7"]
                 [hiccup "1.0.5"]
                 [org.clojure/data.json "2.0.1"]]
  :main lh.cli
  :global-vars {*warn-on-reflection* true}
  :profiles {:test {:resource-paths ["test/resources"]}
             :dev {:resource-paths ["test/resources"]}
             :uberjar {:aot :all
                       :uberjar-name "lighthouse.jar"
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
