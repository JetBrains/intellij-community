import pytest


def f(i/*<# : int #>*/=1):
    pass

class A:
    def f(self, i: int): ...

class B(A):
    def f(self, i/*<# : int #>*/): ...