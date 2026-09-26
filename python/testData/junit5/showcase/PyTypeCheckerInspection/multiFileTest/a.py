from lib import Test

t = Test()
x: int = <warning descr="Expected type 'int', got 'str' instead">t.foo()</warning> # E