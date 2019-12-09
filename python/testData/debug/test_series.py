import pandas as pd
import numpy as np

frame = pd.DataFrame(data=np.arange(8, dtype='f').reshape((4, 2)), columns=['a', 'b'], index=pd.MultiIndex([['s', 'd'], [2, 3]], [[0, 0, 1, 1], [0, 1, 0, 1]]))

series = frame.a

print(series)  # line 7
