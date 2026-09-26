import pandas as pd
# DataFrame columns case
df = pd.DataFrame({"a": [1, 2, 3], "b": [4, 5, 6], "c": [7, 8, 9]})

list(df[['a', 'b']].values)
bb = ["a", "b", "c"]
list(df[bb].values)

# with errors
list(df.<error descr="Name expected">[</error>'a'].values)

<warning descr="Method Series.to_list() is recommended">list<caret>(df['a'].values)</warning>