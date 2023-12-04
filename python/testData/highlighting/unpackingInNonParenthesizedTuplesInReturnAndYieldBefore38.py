def func(xs):
    yield 42, <error descr="Python version 3.6 does not support unpacking without parentheses in yield statements">*xs</error>
    yield from 42, <error descr="Can't use starred expression here">*xs</error>
    return 42, <error descr="Python version 3.6 does not support unpacking without parentheses in return statements">*xs</error>