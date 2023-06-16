from module import foo, bar

foo("str")
foo(5)
foo(<warning descr="Unexpected type(s):(List[int])Possible type(s):(str)(int)">[5]</warning>)

bar("str")
bar(5)
bar(<warning descr="Unexpected type(s):(List[int])Possible type(s):(str)(int)">[5]</warning>)