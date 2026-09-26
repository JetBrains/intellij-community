# coding=utf-8
"""
Counts how many times each module of this fixture was executed.

Counters live on the "sys" module so that they survive even if this module itself
were dropped from sys.modules -- which is exactly what the test checks it isn't.
"""
import sys

_ATTRIBUTE = '_pycharm_behave_fixture_counters'


def count_execution(name):
    counters = getattr(sys, _ATTRIBUTE, None)
    if counters is None:
        counters = {}
        setattr(sys, _ATTRIBUTE, counters)
    counters[name] = counters.get(name, 0) + 1


def get_counters():
    return getattr(sys, _ATTRIBUTE, None) or {}


def reset_counters():
    setattr(sys, _ATTRIBUTE, {})


count_execution('nested_pkg.counters')
