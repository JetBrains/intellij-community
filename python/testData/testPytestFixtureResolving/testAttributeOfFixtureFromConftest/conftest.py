import pytest


@pytest.fixture
def data():
    class TestData:
        key = "value"

    return TestData
