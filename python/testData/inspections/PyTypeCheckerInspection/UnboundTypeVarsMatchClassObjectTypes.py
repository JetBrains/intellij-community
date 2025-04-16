from typing import TypeVar

T = TypeVar('T')
B = TypeVar('B', str)


def f1(p: T):
    return p


f1(str)


def f2(p: B):
    return p


f2(<warning descr="Expected type 'B ≤: str', got 'type[str]' instead">str</warning>)


def g1(p):
    """
    :type p: T 
    """


g1(str)


def g2(p):
    """
    :type p: T <= str 
    """


g2(<warning descr="Expected type 'T ≤: str', got 'type[str]' instead">str</warning>)

xs = list([str])
