import pytest

#
@pytest.fixture
def my_fixture():
    yield "hello"

@pytest.fixture
def ham(my_fixt<caret>):
    return 42


def test_sample_test(my_fixt<caret>):
    pass

def test_ham(ham, my_fixture):
    ham.bit_len<caret>#
    my_fixture.swapca<caret>#

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
