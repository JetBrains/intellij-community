import pytest


def test_reproduce(foo_<caret>fixture1, foo_fixture2):
    assert foo_fixture1 == 1
    assert foo_fixture2 == 2
