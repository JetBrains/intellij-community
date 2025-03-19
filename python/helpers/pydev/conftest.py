import sys
from collections import namedtuple

import pytest

from pydev_tests_python.regression_check import data_regression
from pydev_tests_python.regression_check import datadir
from pydev_tests_python.regression_check import original_datadir

Marker = namedtuple("marker", "skip_condition, default_reason")


MARKERS = {
    "python2": Marker(
        not sys.version_info[0] == 2,
        "test is only applicable for Python 2",
    ),
    "python3": Marker(
        not sys.version_info[0] == 3,
        "test is only applicable for Python 3",
    ),
    "le_python311": Marker(
        not sys.version_info[:2] <= (3, 11),
        "test is only applicable for Python <=3.11",
    ),
    "ge_python312": Marker(
        not sys.version_info >= (3, 12),
        "test is only applicable for Python >=3.12",
    ),
}


def pytest_configure(config):
    for marker_name, (_, default_reason) in MARKERS.items():
        doc_line = "%s(reason): %s" % (marker_name, default_reason)
        config.addinivalue_line("markers", doc_line)


def pytest_collection_modifyitems(config, items):
    for item in items:
        for marker_name, (skip_condition, default_reason) in MARKERS.items():
            if marker_name in item.keywords and skip_condition:
                marker = item.get_closest_marker(marker_name)
                reason = marker.kwargs.get('reason', default_reason)
                item.add_marker(pytest.mark.skip(reason=reason))
