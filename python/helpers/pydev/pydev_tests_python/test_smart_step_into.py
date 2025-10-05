from __future__ import print_function

import pytest

from _pydevd_bundle.smart_step_into import get_stepping_variants
from _pydevd_bundle.pydevd_constants import IS_PY38
from _pydevd_bundle.pydevd_constants import IS_PY39
from _pydevd_bundle.pydevd_constants import IS_PY310
from _pydevd_bundle.pydevd_constants import IS_PY314


@pytest.fixture
def inner_decorator_code():
    def power(exponent):
        def outer(f):
            def inner(*args):
                result = f(*args)
                return result ** exponent
            return inner
        return outer
    return power.__code__.co_consts[1].co_consts[1]


@pytest.fixture
def function_with_try_except_code():
    def f():
        try:
            1 / 0
        except ZeroDivisionError:
            print("Can't divide by zero!")
        else:
            print("Everything is fine.")
    return f.__code__


@pytest.fixture
def returned_object_method():
    def inner():
        class A:
            def __init__(self, z):
                self.z = z

            def foo(self, x):
                y = 2 * x + self.z
                return 1 + y

        def zoo(x):
            y = int((x - 2) / (x - 1))

            return A(y)

        print(zoo(2).foo(2))

    return inner.__code__


def f():
    return 1


def g():
    return 2


def h():
    return 3


@pytest.fixture
def consecutive_calls():
    def i():
        return f() + g() + h()

    return i.__code__


@pytest.mark.python2(reason="Python 3 is required to step into binary operators")
@pytest.mark.xfail(not IS_PY38, reason="PCQA-718")
def test_candidates_for_inner_decorator_py2(inner_decorator_code):
    variants = list(get_stepping_variants(inner_decorator_code))
    assert len(variants) == 1
    assert variants[0].argval == 'f'


@pytest.mark.python3(reason="Python 3 is required to step into binary operators")
@pytest.mark.xfail(IS_PY314, reason='PCQA-943')
def test_candidates_for_inner_decorator_py3(inner_decorator_code):
    variants = list(get_stepping_variants(inner_decorator_code))
    assert len(variants) == 2
    assert variants[0].argval == 'f'
    assert variants[1].argval == '__pow__'


@pytest.mark.python2(reason="Python 3 is required to step into binary operators")
def test_candidates_for_function_with_try_except_py2(function_with_try_except_code):
    variants = list(get_stepping_variants(function_with_try_except_code))
    assert len(variants) == 2
    assert variants[0].argval == 'print'
    assert variants[1].argval == 'print'


@pytest.mark.python3(reason="Python 3 is required to step into binary operators")
def test_candidates_for_function_with_try_except_py3(function_with_try_except_code):
    variants = list(map(lambda instr: instr.argval, get_stepping_variants(function_with_try_except_code)))
    assert '__div__' in variants
    assert 'print' in variants

    if len(variants) > 3:
        assert 'RERAISE' in variants

@pytest.mark.python2(reason="Python 3 is required to step into binary operators")
def test_candidates_for_consecutive_calls_py2(consecutive_calls):
    variants = list(get_stepping_variants(consecutive_calls))
    assert len(variants) == 3
    assert variants[0].argval == 'f'
    assert variants[1].argval == 'g'
    assert variants[2].argval == 'h'


@pytest.mark.python3(reason="Python 3 is required to step into binary operators")
def test_candidates_for_consecutive_calls_py3(consecutive_calls):
    variants = list(get_stepping_variants(consecutive_calls))
    assert len(variants) == 5
    assert variants[0].argval == 'f'
    assert variants[1].argval == 'g'
    assert variants[2].argval == '__add__'
    assert variants[3].argval == 'h'
    assert variants[4].argval == '__add__'


@pytest.mark.xfail(IS_PY38 or IS_PY39 or IS_PY310 or IS_PY314, reason='PCQA-592')
def test_candidates_for_returned_object_method(returned_object_method):
    variants = list(get_stepping_variants(returned_object_method))
    assert len(variants) == 3
    assert variants[0].argval == 'zoo'
    assert variants[1].argval == 'foo'
    assert variants[2].argval == 'print'
