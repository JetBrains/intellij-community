def method_a():
    """
    :rtype: dict or int
    """
    pass


def method_b(d):
    """
    :type d: dict
    """
    pass


def f():
    var = method_a()
    if isinstance(var, int):
        return var
    method_b(var)  # pass