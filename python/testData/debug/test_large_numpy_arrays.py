import numpy as np
import pandas as pd

nd = np.arange(1000)

s = pd.Series(name='foo', data=nd)

df = pd.DataFrame({'bar': nd})

df.size