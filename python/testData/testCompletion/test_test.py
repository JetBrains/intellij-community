import pytest

#
@pytest.fixture
def my_fixture():
    pass

@pytest.fixture
def ham(my_fixt<caret>):
    pass


def test_sample_test(my_fixt<caret>):
    pass

@pytest.mark.parametrize(('spam,eggs'), [(1,1), (2,3), (3,3)])
@pytest.mark.parametrize('first,second', [(1,1), (2,3), (3,3)])
def test_sample(spa<caret>,egg<caret>,firs<caret>,secon<caret>):
    pass