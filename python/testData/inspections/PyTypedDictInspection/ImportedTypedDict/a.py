from test import Test1, Base

b = Base(a=2)
t = Test1(a=2)

a = t["a"]
a = t[<warning descr="TypedDict \"Test1\" has no key 'e'">'e'</warning>]
