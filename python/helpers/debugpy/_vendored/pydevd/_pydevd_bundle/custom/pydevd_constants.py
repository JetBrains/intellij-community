import os
import sys

try:
    xrange = xrange
except:
    # Python 3k does not have it
    xrange = range

NUMPY_NUMERIC_TYPES = "biufc"
NUMPY_FLOATING_POINT_TYPES = "fc"
IS_PYCHARM = True

#=======================================================================================================================
# Python 3?
#=======================================================================================================================
IS_PY3K = False
IS_PY34_OR_GREATER = False
IS_PY36_OR_GREATER = False
IS_PY37_OR_GREATER = False
IS_PY36_OR_LESSER = False
IS_PY38_OR_GREATER = False
IS_PY38 = False
IS_PY39 = False
IS_PY39_OR_GREATER = False
IS_PY310 = False
IS_PY310_OR_GREATER = False
IS_PY311 = False
IS_PY311_OR_GREATER = False
IS_PY312_OR_GREATER = False
IS_PY312_OR_LESSER = False
IS_PY313 = False
IS_PY313_OR_GREATER = False
IS_PY313_OR_LESSER = False
IS_PY314 = False
IS_PY2 = True
IS_PY27 = False
IS_PY24 = False
try:
    if sys.version_info[0] >= 3:
        IS_PY3K = True
        IS_PY2 = False
        IS_PY34_OR_GREATER = sys.version_info >= (3, 4)
        IS_PY36_OR_GREATER = sys.version_info >= (3, 6)
        IS_PY37_OR_GREATER = sys.version_info >= (3, 7)
        IS_PY36_OR_LESSER = sys.version_info[:2] <= (3, 6)
        IS_PY38 = sys.version_info[0] == 3 and sys.version_info[1] == 8
        IS_PY38_OR_GREATER = sys.version_info >= (3, 8)
        IS_PY39 = sys.version_info[0] == 3 and sys.version_info[1] == 9
        IS_PY39_OR_GREATER = sys.version_info >= (3, 9)
        IS_PY310 = sys.version_info[0] == 3 and sys.version_info[1] == 10
        IS_PY310_OR_GREATER = sys.version_info >= (3, 10)
        IS_PY311 = sys.version_info[0] == 3 and sys.version_info[1] == 11
        IS_PY311_OR_GREATER = sys.version_info >= (3, 11)
        IS_PY312_OR_GREATER = sys.version_info >= (3, 12)
        IS_PY312_OR_LESSER = sys.version_info[:2] <= (3, 12)
        IS_PY313 = sys.version_info[0] == 3 and sys.version_info[1] == 13
        IS_PY313_OR_GREATER = sys.version_info >= (3, 13)
        IS_PY313_OR_LESSER = sys.version_info[:2] <= (3, 13)
        IS_PY314 = sys.version_info[0] == 3 and sys.version_info[1] == 14
    elif sys.version_info[0] == 2 and sys.version_info[1] == 7:
        IS_PY27 = True
    elif sys.version_info[0] == 2 and sys.version_info[1] == 4:
        IS_PY24 = True
except AttributeError:
    pass  # Not all versions have sys.version_info


if IS_PY3K:

    def dict_keys(d):
        return list(d.keys())

    def dict_values(d):
        return list(d.values())

    dict_iter_values = dict.values

    def dict_iter_items(d):
        return d.items()

    def dict_items(d):
        return list(d.items())

else:
    def dict_keys(d):
        return d.keys()

    try:
        dict_iter_values = dict.itervalues
    except:
        try:
            dict_iter_values = dict.values  # Older versions don't have the itervalues
        except:

            def dict_iter_values(d):
                return d.values()

    try:
        dict_values = dict.values
    except:

        def dict_values(d):
            return d.values()

    def dict_iter_items(d):
        try:
            return d.iteritems()
        except:
            return d.items()

    def dict_items(d):
        return d.items()


class ValuesPolicy:
    SYNC = 0
    ASYNC = 1
    ON_DEMAND = 2


LOAD_VALUES_POLICY = ValuesPolicy.SYNC
if os.getenv('PYDEVD_LOAD_VALUES_ASYNC', 'False') == 'True':
    LOAD_VALUES_POLICY = ValuesPolicy.ASYNC
if os.getenv('PYDEVD_LOAD_VALUES_ON_DEMAND', 'False') == 'True':
    LOAD_VALUES_POLICY = ValuesPolicy.ON_DEMAND
DEFAULT_VALUES_DICT = {ValuesPolicy.ASYNC: "__pydevd_value_async", ValuesPolicy.ON_DEMAND: "__pydevd_value_on_demand"}