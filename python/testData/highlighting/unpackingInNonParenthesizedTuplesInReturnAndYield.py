def func(xs):
    yield 42, *xs
    yield from 42, <error descr="Can't use starred expression here">*xs</error>
    return 42, *xs