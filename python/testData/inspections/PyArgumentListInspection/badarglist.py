# bad argument list samples
class A:
    def foo(self, x, y):
        pass

    # no self, but so what
    def bar(one, two):
        pass

a = A()

a.foo(1,2)

a.bar(<warning descr="Parameter 'two' unfilled">)</warning>;


def f1():
    pass

f1()
f1(<warning descr="Unexpected argument">1</warning>)
f1(<warning descr="Unexpected argument">a = 1</warning>)


def f2(a):
    pass


f2(<warning descr="Parameter 'a' unfilled">)</warning> # ok, fail
f2(1) # ok, pass
f2(1, <warning descr="Unexpected argument">2</warning>) # ok, fail
f2(a = 1) # ok, pass
f2(<warning descr="Unexpected argument">b = 1</warning><warning descr="Parameter 'a' unfilled">)</warning> # ok, fail
f2(a = 1, <warning descr="Unexpected argument">b = 2</warning>) # ok, fail


def f3(a, b):
    pass

f3(1, 2)
f3(1, 2, <warning descr="Unexpected argument">3</warning>)
f3(b=2, a=1)
f3(b=1, <error descr="Keyword argument repeated">b=2</error>, a=1)
f3(1, b=2)
f3(a=1, <error descr="Positional argument after keyword argument">2</error><warning descr="Parameter 'b' unfilled">)</warning>

def f4(a, *b):
    pass

f4(1)
f4(1, 2)
f4(1, 2, 3)
f4(1, *(2, 3))
f4(*(1,2,3))
f4(a=1, <error descr="Positional argument after keyword argument">2</error>, <error descr="Positional argument after keyword argument">3</error>)
