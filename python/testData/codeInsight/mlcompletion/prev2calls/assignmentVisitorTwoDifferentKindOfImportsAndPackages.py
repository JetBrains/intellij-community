import numpy as np

from tensorflow.examples.tutorials.mnist import input_data
mnist = input_data.read_data_sets("/tmp/data/", one_hot=True)
counts = np.zeros(shape=(42, 42))
for i in range(42):
    counts[idx[i]] += mnist.train.labels[i]

<caret>


