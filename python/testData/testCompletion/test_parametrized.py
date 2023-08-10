import pytest


@pytest.mark.parametrize("test_input,expected", [
    ("3+5", 8),
    ("2+4", 6),
])
@pytest.mark.parametrize("x", [0, 1])
@pytest.mark.parametrize("y", [2, 3])
def test_returns_correct_result(test_input, expected, x, y):  # False positive: unused parameters
    y.bit_len<caret>#
    x.bit_len<caret>#
    test_input.len<caret>#
    expected.bit_len<caret>#

@pytest.mark.parametrize(('x', 'y'), [(1, 2, 3, 4)])  # Too many values in tuple, should be 2
def test_wrong_number_of_parameters(x, y):
    x.bit_len<caret>#

@pytest.mark.parametrize("y", ['2', 3])
def test_foo(y):
   y.bit_len<caret>#
   y.__xor<caret>#

@pytest.mark.parametrize("y", 3)
def test_int_foo(y):
   y.__xor<caret>#


@pytest.mark.parame<caret>


@pytest.fixture()
def my_spam_fix(request) -> str:
    return 'x' * request.param


@pytest.mark.parametrize('my_spam_fix', (1, 2, 3), indirect=True)
def test_indirect(my_spam_fix):
    assert my_spam_fix.fin<caret>#


@pytest.mark.parametrize('my_spam_fix', (1, 2, 3))
def test_no_indirect(my_spam_fix):
    assert my_spam_fix.__xor<caret>#


@pytest.fixture()
def my_eggs_fixture(request) -> str:
    return 'x' * request.param

@pytest.mark.parametrize('my_spam_fix, my_eggs_fixture', (1, 2), indirect=["my_spam_fix"])
def test_indirect_two(my_spam_fix, my_eggs_fixture):
    assert my_spam_fix.fin<caret>#
    assert my_eggs_fixture.__xor<caret>#