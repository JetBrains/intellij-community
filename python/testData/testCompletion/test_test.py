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

@pytest.mark.parametrize('first,second', [('a', 1), (1, 'a')])
def test_sample(first, second):

    first.bit_lengt<caret>#
    first.forma<caret>#
    first.__xor_<caret>#
    second.bit_lengt<caret>#
    second.forma<caret>#
    second.__xor_<caret>#
