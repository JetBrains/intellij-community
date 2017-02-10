import pandas as pd
import numpy as np

# multiindex rows
frame1 = pd.DataFrame(data=np.random.randint(0, high=10, size=(4, 2)), columns=['a', 'b'], index=pd.MultiIndex([['s', 'd'], [2, 3]], [[0, 0, 1, 1], [0, 1, 0, 1]]))
print(frame1) #line 6

# multiindex columns
frame2 = pd.DataFrame(np.random.random((4, 4)))
frame2.columns = pd.MultiIndex.from_product([[1, 2], [1, 'B']])
print(frame2) # line 11