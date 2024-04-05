import pytest


def foo(x):
    return x * x

def tes<caret>t_foo():
    """
    >>> foo(2)
    4
    """
    assert True
