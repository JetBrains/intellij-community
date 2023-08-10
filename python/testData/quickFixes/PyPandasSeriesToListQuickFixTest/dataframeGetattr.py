import pandas as pd
# DataFrame columns case
df = pd.DataFrame({"a": [1, 2, 3], "b": [4, 5, 6], "c": [7, 8, 9]})

<warning descr="Method Series.to_list() is recommended">list<caret>(df.b.values)</warning>