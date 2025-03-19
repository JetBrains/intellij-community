def foo(x):
    return x * x


def test_false():
    """
    >>> foo(2)
    2
    """
    assert foo(2) == 2
