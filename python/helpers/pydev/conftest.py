import sys

import pytest

from pydev_tests_python.regression_check import data_regression
from pydev_tests_python.regression_check import datadir
from pydev_tests_python.regression_check import original_datadir


def pytest_configure(config):
    config.addinivalue_line("markers", "python2(reason): skip if not Python 2")
    config.addinivalue_line("markers", "python3(reason): skip if not Python 3")


def pytest_collection_modifyitems(config, items):
    for item in items:
        if 'python2' in item.keywords and sys.version_info[0] > 2:
            default_reason = 'test is only applicable for Python 2'
            marker = item.get_closest_marker('python2')
            reason = marker.kwargs.get('reason', default_reason)
            item.add_marker(pytest.mark.skip(reason=reason))

        if 'python3' in item.keywords and sys.version_info[0] < 3:
            default_reason = 'test is only applicable for Python 3'
            marker = item.get_closest_marker('python3')
            reason = marker.kwargs.get('reason', default_reason)
            item.add_marker(pytest.mark.skip(reason=reason))
