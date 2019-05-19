import pytest

#
@pytest.fixture
def foo():
    pass

@pytest.mark.parametrize(('spam,eggs'), [(1,1), (2,3), (3,3)])
@pytest.mark.parametrize('first,second', [(1,1), (2,3), (3,3)])
def test_sample(<weak_warning descr="Parameter 'apple' value is not used">apple</weak_warning>, foo, spam<warning descr="Following arguments are not declared but provided by decorator: [eggs, first, second]">)</warning>:
    pass

@pytest.mark.parametrize('foo,', [False, True])
def test_foo(foo):
    pass

@pytest.mark.parametrize('foo,', [False, True])
def test_spam(<warning descr="Following arguments are not declared but provided by decorator: [foo]">)</warning>:
    pass