class A(B):
    def __init__(self):
        <warning descr="Python version 2.4, 2.5, 2.6, 2.7 do not support this syntax. super() should have arguments in Python 2">super()</warning>

<warning descr="Python version 3.1, 3.2, 3.3, 3.4 do not have method cmp">cmp()</warning>
<warning descr="Python version 3.0, 3.1, 3.2, 3.3, 3.4 do not have method reduce">reduce()</warning>
<warning descr="Python version 2.4 does not have method all">all()</warning>

<warning descr="Python version 3.0, 3.1, 3.2, 3.3, 3.4 do not have method buffer">buffer()</warning>

def foo(a,b,c):
    print (a,b,c)

args=['b']
foo('a', c='c', *args) # OK
foo('a', *args, <warning descr="Python version < 2.6 doesn't support this syntax. Named parameter cannot appear past *arg or **kwarg.">c='c'</warning>) # Not OK