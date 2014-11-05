(ns leiningen.launch4j
  (:require [leiningen.launch4j.zip :as zip]
            [leiningen.launch4j.xml :as xml]
            [clojure.java.io :as io]
            [leiningen.core.main :as main]
            [leiningen.core.user :as user])
  (:import net.sf.launch4j.Log
           net.sf.launch4j.Builder
           net.sf.launch4j.config.ConfigPersister))

(def base-url "http://www.zephyrizing.net/lein-launch4j/resources")

(defn read-config [config-file]
  (.. (ConfigPersister/getInstance)
      (load config-file)))

(defn build-project [bin-dir]
  (.. (Builder. (Log/getConsoleLog) bin-dir)
      (build)))

(defn validate-filename [file-name]
  (when file-name
    (let [f (io/file file-name)]
      (if (.exists f)
        (.getCanonicalFile f)
        (do
          (main/warn (str file-name " does not exist"))
          ((System/exit 0)))))))

(defn download-suffix
  "Get the platform specific download suffix."
  []
  (let [os       (System/getProperty "os.name")
        version  (System/getProperty "os.version")]
    (cond
     (and (= os "Mac OS X")
          (neg? (compare version "10.8"))) "macosx"
          (= os "Mac OS X")                "macosx-10.8"
          (= os "Linux")                   "linux"
          (= os "Windows")                 "win32")))

(defn init-launch4j
  "Make sure the needed binaries are downloaded."
  []
  (let [lein-home (io/file (user/leiningen-home))
        zip-url (io/as-url (str base-url "/launch4j-3.4-"
                                (download-suffix)
                                ".zip"))]
    (when (not (.exists (io/file lein-home "launch4j")))
     (with-open [zip-stream (zip/zip-stream
                             (io/input-stream zip-url))]
       (zip/extract-stream zip-stream lein-home)))
    (io/file lein-home "launch4j")))

(defn launch4j
  "Wrap your leiningen project into a Windows .exe

Add :main to your project.clj to specify the namespace that contains your
-main function."
  [project & args]
  (cond (:main project)
        ;; Make sure we have launch4j installed
        (if-let [launch4j-home (init-launch4j)]
          (let [target  (io/file (:target-path project))
                jarfile ""
                xmlfile (io/file target "config.xml")
                outfile (io/file target
                                 (or (:exe-name project)
                                     (str (:name project)
                                          "-"
                                          (:version project) ".exe")))
                options (merge {:jar     jarfile
                                :outfile outfile}
                               (:launch4j project))]
            (with-open [xmlout (io/writer xmlfile)]
              (xml/emit-config options xmlout))
            (validate-filename jarfile)
            (read-config
             (validate-filename xmlfile))
            (build-project
             (validate-filename launch4j-home))))
        :else (main/warn "Could not run launch4j, you must have a :main"
                         "namespace specified.")))
