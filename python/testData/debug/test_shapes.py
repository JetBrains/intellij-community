import numpy as np
import pandas as pd


class MyCollection:
    def __init__(self, size):
        self.size = size

    def __len__(self):
        return self.size


list1 = [i for i in range(120)]

dict1 = {'a': 1, 'b': 2}

custom = MyCollection(5)

df1 = pd.DataFrame({'row': [0, 1, 2],
                    'One_X': [1.14444, 1.144444, 1.144444],
                    'One_Y': [1.24444, 1.244444, 1.244444],
                    'Two_X': [1.11, 1.11, 1.11],
                    'Two_Y': [1.22, 1.22, 1.22],
                    'LABELS': ['A', 'B', 'C']},
                   index=['a', 'b', 'c'])

n_array = np.random.random_sample((3, 2))

series = pd.Series([10, 20, 30, 40, 50], index=['a', 'b', 'c', 'd', 'e'])


class MyShapeCollection:
    def __init__(self, shape):
        self.shape = shape


custom_shape = MyShapeCollection((3,))
custom_shape2 = MyShapeCollection((2, 3))

print("Executed")
