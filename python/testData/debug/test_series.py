import pandas as pd
import numpy as np

frame = pd.DataFrame(data=np.random.randint(0, high=10, size=(4, 2)), columns=['a', 'b'], index=pd.MultiIndex([['s', 'd'], [2, 3]], [[0, 0, 1, 1], [0, 1, 0, 1]]))

series = frame.a

print(series)  # line 7
