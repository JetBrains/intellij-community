import pytest

#
@pytest.fixture
def <caret>my_rename_fixture():
    return 1


def test_sample(my_rename_fixture):
    my_rename_fixture.bit_length
