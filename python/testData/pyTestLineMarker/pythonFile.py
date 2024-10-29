import pytest


def foo(x):
    return x + 1


def te<caret>st_foo():
    assert foo(1) == 2
