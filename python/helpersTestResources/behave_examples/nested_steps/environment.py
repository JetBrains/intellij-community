# coding=utf-8
# behave executes this file directly, so it cannot import the counters module of
# the nested package. It counts on the "sys" module the same way.
import sys

_COUNTERS = sys.__dict__.setdefault('_pycharm_behave_fixture_counters', {})
_COUNTERS['environment'] = _COUNTERS.get('environment', 0) + 1


def before_all(context):
    _COUNTERS['before_all'] = _COUNTERS.get('before_all', 0) + 1
