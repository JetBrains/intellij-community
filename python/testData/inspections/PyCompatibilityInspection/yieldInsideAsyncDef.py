<warning descr="Python versions < 3.5 do not support this syntax">async</warning> def foo(x):
    <warning descr="Python versions < 3.5 do not support this syntax">await x</warning>
    <warning descr="Python version 3.5 does not support 'yield' inside async functions">yield x</warning>
    <error descr="Python does not support 'yield from' inside async functions"><warning descr="Python versions < 3.3 do not support this syntax. Delegating to a subgenerator is available since Python 3.3; use explicit iteration over subgenerator instead.">yield from x</warning></error>
    <warning descr="Python versions < 3.3 do not allow 'return' with argument inside generator.">return x</warning>
