import tensorflow as tf
import numpy as np

labels_map = [np.argmax(c) for c in counts]
labels_map = tf.convert_to_tensor(labels_map)

cluster_label = tf.nn.embedding_lookup(labels_map, cluster_idx)

correct_prediction = tf.equal(cluster_label, tf.cast(tf.argmax(Y, 1), tf.int32))
accuracy_op = tf.reduce_mean(tf.cast(correct_prediction, tf.float32))

<caret>