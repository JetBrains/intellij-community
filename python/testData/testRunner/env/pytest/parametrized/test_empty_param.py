import pytest


@pytest.mark.parametrize("val", [
    "",
    "other",
])
def test_params(val):
    assert True