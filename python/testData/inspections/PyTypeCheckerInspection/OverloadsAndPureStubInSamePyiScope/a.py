from module import foo, bar

foo("str")
foo(5)
foo(<warning descr="No overload of 'foo' matches the arguments. Argument types: (List[int]). Expected one of: (p: str), (p: int)">[5]</warning>)

bar("str")
bar(5)
bar(<warning descr="No overload of 'bar' matches the arguments. Argument types: (List[int]). Expected one of: (p: str), (p: int)">[5]</warning>)