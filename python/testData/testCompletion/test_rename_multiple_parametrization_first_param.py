import pytest

@pytest.mark.parametrize("a", [1, 3, 4, 5, 6, 7])
@pytest.mark.parametrize("b", [2, 6, 8, 10, 12, 14])
def test_exmpl(<caret>a, b):
    assert (b - a) > 0
