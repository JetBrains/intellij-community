import pytest
from simpleUnpassedFixture import foo

def test_():
    assert <warning descr="Fixture 'foo' is not requested by test functions or @pytest.mark.usefixtures marker"><caret>foo</warning> == 1
