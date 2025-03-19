import pytest
from FixtureInDecorator import foo

def test_():
    assert <warning descr="Fixture 'foo' is not requested by test functions or @pytest.mark.usefixtures marker">foo</warning> == 1
