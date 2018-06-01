import pytest


@pytest.mark.parametrize("test_input,expected", [
    ("3+5", 8),
    ("2+4", 6),
])
@pytest.mark.parametrize("x", [0, 1])
@pytest.mark.parametrize("y", [2, 3])
def test_returns_correct_result(test_input, expected, x, y):  # False positive: unused parameters
    y.bit_len<caret>
    x.bit_len<caret>
    test_input.len<caret>
    expected.bit_len<caret>
