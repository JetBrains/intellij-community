import pandas as pd

df1 = pd.DataFrame({'first': [0, 1, 2],
                    'second': [{1: (1, 2)}, {2: (3,)}, {4: 5}],
                    'third': [(1, 2, 3), (), (4,)]})
print(df1)  ###line 6
