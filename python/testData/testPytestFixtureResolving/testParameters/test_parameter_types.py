import pytest

@pytest.mark.parametrize("param1, param2", [
    (5, 9)
    pytest.param(1, "foo", id="namedparam")
])
def test_named_parameter(param1, para<caret>m2):
    pass
