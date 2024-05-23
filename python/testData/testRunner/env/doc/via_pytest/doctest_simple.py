import pytest


def foo(x):
    return x * x


class TestFooClass:
    """
    Checks function foo(x) = x * x
    >>> foo(1)
    1
    >>> foo(2)
    4
    """

    def test_true(self):
        """
        >>> foo(2)
        4
        """
        assert foo(2) == 4

    def test_false(self):
        """
        >>> foo(2)
        2
        """
        assert foo(2) == 2
