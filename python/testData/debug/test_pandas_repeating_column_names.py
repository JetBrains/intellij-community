import pandas as pd

a = pd.DataFrame([i for i in range(10)])
b = pd.Series([i for i in range(10)])
c = pd.concat((a, b), axis=1)
c.columns = ['A', 'A']

print(c.head())
