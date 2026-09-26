from test import Test1, Base

b = Base(a=2)
t = Test1(a=2)
t = Test1(<warning descr="Parameter 'a' unfilled">)</warning>
