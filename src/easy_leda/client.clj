(ns easy-leda.client
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]))

;; https://stackoverflow.com/questions/5419125/reading-a-zip-file-using-java-api-from-clojure

(def download-url "http://150.165.85.29:81/download")

;; curl --header "Origin: http://150.165.85.29:81"
;; --header "Upgrade-Insecure-Requests: 1"
;; --header "Content-Type: application/x-www-form-urlencoded"
;; --header "User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36"
;; --header "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3"
;; -X POST
;; -d "id=R10-01&matricula=118110035"
;; -o result.zip
;; http://150.165.85.29:81/download


(defn download [mat exc]
  (client/post download-url
   {:headers {:upgrade-insecure-requests 1
              :origin "http://150.165.85.29:81"
              :user-agent "easy_leda"
              :accept "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3"}
    :form-params {:id exc
                  :matricula mat}
    :as :stream}))

(defn dl->zip [out res]
  (let [path (str out
                  (get
                   (re-find #"filename=\"(R.*)-environment.zip\""
                            (get-in res [:headers :content-disposition])) 1)
                  ".zip")]
    (io/copy (get-in res [:body])
             (io/file path)
             )
    path))

(defn zip->entries [zip]
  (lazy-seq
   (when-let [entry (.getNextEntry zip)]
     (cons entry (zip->entries zip)))))

(defn entry->file [entry zip out]
  (when-not (.isDirectory entry)
    (let [file (io/file out (.getName entry))
          buff-size 4096
          buffer (byte-array buff-size)
          folder (io/file (.getParent file))]
      (.mkdirs folder)
      (with-open [out-file (io/output-stream file)]
        (loop [size (.read zip buffer)]
          (when (> size 0)
            (.write out-file buffer 0 size)
            (recur (.read zip buffer))))))))

(defn get-path [out res]
  (str out
       (get
        (re-find #"filename=\"(R.*)-environment.zip\""
                 (get-in res [:headers :content-disposition])) 1)))

(defn zip->files [out zip]
  (loop [entry (.getNextEntry zip)]
    (if entry
      (do (entry->file entry zip out)
          (recur (.getNextEntry zip)))
      out)))

(defn res->stream [res]
  (-> res
      (:body)
      (io/input-stream)
      (java.util.zip.ZipInputStream.)))

(defn dl->folder [out res]
  (->> res
       (res->stream)
       (zip->files (get-path "./" res))
       ))

(defn get-exercise [mat exc]
  (->> (download mat exc)
       (dl->folder "./")))
