import json
from abc import abstractmethod
from datetime import datetime

try:
    import numpy
except ImportError:
    numpy = None

try:
    import pandas
except ImportError:
    pandas = None


# Parameter 'value' can also be pandas.DataFrame
def _standardize_dict(value):
    result = {}
    for k, v in value.items():
        result[_standardize_value(k)] = _standardize_value(v)

    return result


def is_int(v):
    return isinstance(v, int) or (numpy and isinstance(v, numpy.integer))


def is_float(v):
    return isinstance(v, float) or (numpy and isinstance(v, numpy.floating))


def is_number(v):
    return is_int(v) or is_float(v)


def is_shapely_geometry(v):
    try:
        from shapely.geometry.base import BaseGeometry
        return isinstance(v, BaseGeometry)
    except ImportError:
        return False


def _standardize_value(v):
    if v is None:
        return v
    if isinstance(v, bool):
        return bool(v)
    if is_int(v):
        return int(v)
    if isinstance(v, str):
        return str(v)
    if is_float(v):
        return float(v)
    if isinstance(v, dict) or (pandas and isinstance(v, pandas.DataFrame)):
        return _standardize_dict(v)
    if isinstance(v, list):
        return [_standardize_value(elem) for elem in v]
    if isinstance(v, tuple):
        return tuple(_standardize_value(elem) for elem in v)
    if (numpy and isinstance(v, numpy.ndarray)) or (pandas and isinstance(v, pandas.Series)):
        return _standardize_value(v.tolist())
    if isinstance(v, datetime):
        return v.timestamp() * 1000  # convert from second to millisecond
    if isinstance(v, CanToDataFrame):
        return _standardize_dict(v.to_data_frame())
    if is_shapely_geometry(v):
        from shapely.geometry import mapping
        return json.dumps(mapping(v))
    try:
        return repr(v)
    except Exception as e:
        # TODO This needs a test case; Also exception should be logged somewhere
        raise Exception('Unsupported type: {0}({1})'.format(v, type(v)))


class CanToDataFrame:
    @abstractmethod
    def to_data_frame(self):  # -> pandas.DataFrame
        pass
