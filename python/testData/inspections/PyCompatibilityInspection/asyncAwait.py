<warning descr="Python version 2.6, 2.7, 3.4 do not support this syntax">async</warning> def foo(x):
    <warning descr="Python version 2.6, 2.7, 3.4 do not support this syntax">async</warning> with x:
        y = <warning descr="Python version 2.6, 2.7, 3.4 do not support this syntax">await x</warning>
        if <warning descr="Python version 2.6, 2.7, 3.4 do not support this syntax">await y</warning>:
            return <warning descr="Python version 2.6, 2.7, 3.4 do not support this syntax">await z</warning>
    <warning descr="Python version 2.6, 2.7, 3.4 do not support this syntax">async</warning> for y in x:
        pass
