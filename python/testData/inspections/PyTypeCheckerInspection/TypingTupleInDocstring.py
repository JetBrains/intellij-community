from typing import Tuple


def test1(name: Tuple[int, int]):
    pass


test1((1, 2))


def test2(name):
    """
    :type name: Tuple[int, int]
    """
    pass


test2((1, 2))
test2(<warning descr="Expected type 'Tuple[int, int]', got 'Tuple[int, str]' instead">(1, 'foo')</warning>)
