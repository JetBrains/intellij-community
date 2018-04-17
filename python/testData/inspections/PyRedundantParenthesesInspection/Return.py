def f1(x):
    return (x, *x)


def f2(x):
    return <weak_warning descr="Remove redundant parentheses">(x, x)</weak_warning>
