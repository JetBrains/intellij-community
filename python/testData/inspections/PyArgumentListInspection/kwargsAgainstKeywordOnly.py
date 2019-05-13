def f1(arg):
    return 2

def f2(*, arg):
    return 2


def test(**kwargs):
    f1(**kwargs)
    f2(**kwargs)
