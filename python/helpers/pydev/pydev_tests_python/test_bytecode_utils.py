from __future__ import print_function
import pytest
from _pydevd_bundle import pydevd_bytecode_utils


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


def test_candidates_for_inner_decorator(inner_decorator_code):
    variants = pydevd_bytecode_utils.get_smart_step_into_candidates(
        inner_decorator_code)
    assert len(variants) == 2
    assert variants[0].argval == 'f'
    assert variants[1].argval == '__pow__'


def test_candidates_for_function_with_try_except(function_with_try_except_code):
    variants = pydevd_bytecode_utils.get_smart_step_into_candidates(
        function_with_try_except_code)
    assert len(variants) == 3
    assert variants[0].argval == '__div__'
    assert variants[1].argval == 'print'
    assert variants[2].argval == 'print'


def test_candidates_for_consecutive_calls(consecutive_calls):
    variants = pydevd_bytecode_utils.get_smart_step_into_candidates(consecutive_calls)
    assert len(variants) == 5
    assert variants[0].argval == 'f'
    assert variants[1].argval == 'g'
    assert variants[2].argval == '__add__'
    assert variants[3].argval == 'h'
    assert variants[4].argval == '__add__'
