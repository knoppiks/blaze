(ns blaze.db.impl.index.resource-as-of-test-util
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [blaze.db.impl.index.resource-handle :as rh]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn decode-key-human
  ([] (bb/allocate-direct 128))
  ([buf]
   (let [tid (bb/get-int! buf)
         id-size (- (bb/remaining buf) codec/t-size)]
     {:type (codec/tid->type tid)
      :id (codec/id-string (bs/from-byte-buffer buf id-size))
      :t (codec/descending-long (bb/get-long! buf))})))


(defn decode-value-human
  ([] (bb/allocate-direct (+ codec/hash-size Long/BYTES)))
  ([buf]
   (let [hash (bs/from-byte-buffer buf codec/hash-size)
         state (bb/get-long! buf)]
     {:hash hash
      :num-changes (rh/state->num-changes state)
      :op (rh/state->op state)})))


(defn decode-index-entry [[k v]]
  [(decode-key-human (bb/wrap k))
   (decode-value-human (bb/wrap v))])
