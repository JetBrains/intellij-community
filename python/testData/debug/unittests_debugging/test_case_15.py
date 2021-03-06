import pytest


def test_func():
    with pytest.raises(Exception) as e:
        assert True
