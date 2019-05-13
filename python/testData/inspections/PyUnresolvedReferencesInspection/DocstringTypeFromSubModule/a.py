from pkg1 import C


def foo():
    """
    :rtype: pkg1.C
    """
    return C(0)


obj = foo()
obj.bar
