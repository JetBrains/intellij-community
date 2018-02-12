import pytest
@pytest.mark.parametrize("test_input,expected", [
    ("three plus file", 8),
    ("(2)+(4)", 6),
    (" six times nine.", 42),
])
def test_eval(test_input, expected):
    assert eval(test_input) == expected