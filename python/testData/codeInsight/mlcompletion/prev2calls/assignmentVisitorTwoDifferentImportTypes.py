import tensorflow as tf
from tensorflow.contrib.factorization import KMeans

kmeans = KMeans(inputs=tf.placeholder(tf.float32, shape=[None, 42]), num_clusters=42, distance_metric='cosine', use_mini_batch=True)

training_graph = kmeans.training_graph()
print(training_graph)

<caret>