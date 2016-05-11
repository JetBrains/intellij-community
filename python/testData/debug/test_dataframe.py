import pandas as pd
import numpy as np
df1 = pd.DataFrame({'row': [0, 1, 2],
                    'One_X': [1.1, 1.1, 1.1],
                    'One_Y': [1.2, 1.2, 1.2],
                    'Two_X': [1.11, 1.11, 1.11],
                    'Two_Y': [1.22, 1.22, 1.22]})
print(df1)  ###line 8

df2 = pd.DataFrame({'row': [0, 1, 2],
                    'One_X': [1.1, 1.1, 1.1],
                    'One_Y': [1.2, 1.2, 1.2],
                    'Two_X': [1.11, 1.11, 1.11],
                    'Two_Y': [1.22, 1.22, 1.22],
                    'LABELS': ['A', 'B', 'C']})
print(df2) ##line 16

df3 = pd.DataFrame(data={'Province' : ['ON','QC','BC','AL','AL','MN','ON'],
                         'City' : ['Toronto','Montreal','Vancouver','Calgary','Edmonton','Winnipeg','Windsor'],
                         'Sales' : [13,6,16,8,4,3,1]})
table = pd.pivot_table(df3,values=['Sales'],index=['Province'],columns=['City'],aggfunc=np.sum,margins=True)
table.stack('City')
print(df3)

df4 = pd.DataFrame({'row': np.random.random(10000),
                    'One_X': np.random.random(10000),
                    'One_Y': np.random.random(10000),
                    'Two_X': np.random.random(10000),
                    'Two_Y': np.random.random(10000),
                    'LABELS': ['A'] * 10000})
print(df4) ##line 31

