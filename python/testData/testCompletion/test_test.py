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


def test_type(first, second:set):
    second.updat<caret>#


class TestFoo1:
    @pytest.fixture
    def my_fixture(self):
        return 42

    @pytest.fixture
    def another_fix(): pass

class TestFoo2:
    @pytest.fixture
    def my_fixture(self):
        return {"D": 42 }

    @pytest.fixture
    def class_only_fixture(): return 1

    def test_bar(self, my_fix<caret>, ha<caret>, class_only_f<caret>):
        pass

    # No completion for another: different class
    def test_foo(self, my_fixture, another_fix<caret>):
        assert my_fixture.ke<caret>

# No completion, declated in class
def test_bar(class_only_fix<caret>):
        pass