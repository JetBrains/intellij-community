1, *x
(1, *x)
[1, *x]
{1, *x}

if <error descr="Can't use starred expression here">*x</error>:
    pass

1 + (<error descr="Can't use starred expression here">*x</error>)
1 + (*x,)


def f(x):
    return x, <error descr="Can't use starred expression here">*x</error>


def g(x):
    yield from x, <error descr="Can't use starred expression here">*x</error>
    yield x, <error descr="Can't use starred expression here">*x</error>