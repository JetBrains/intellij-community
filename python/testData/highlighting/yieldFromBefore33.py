def f(g):
    <error descr="Python versions < 3.3 do not support this syntax. Delegating to a subgenerator is available since Python 3.3; use explicit iteration over subgenerator instead.">yield from g()</error>
