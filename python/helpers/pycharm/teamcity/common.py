# coding=utf-8

import sys
import traceback
import inspect


_max_reported_output_size = 1 * 1024 * 1024
_reported_output_chunk_size = 50000


def limit_output(data):
    return data[:_max_reported_output_size]


def split_output(data):
    while len(data) > 0:
        if len(data) <= _reported_output_chunk_size:
            yield data
            data = ''
        else:
            yield data[:_reported_output_chunk_size]
            data = data[_reported_output_chunk_size:]


def is_string(obj):
    if sys.version_info >= (3, 0):
        return isinstance(obj, str)
    else:
        return isinstance(obj, basestring)


def get_class_fullname(something):
    if inspect.isclass(something):
        cls = something
    else:
        cls = something.__class__

    module = cls.__module__
    if module is None or module == str.__class__.__module__:
        return cls.__name__
    return module + '.' + cls.__name__


def convert_error_to_string(err):
    try:
        exctype, value, tb = err
        return ''.join(traceback.format_exception(exctype, value, tb))
    except:
        tb = traceback.format_exc()
        return "*FAILED TO GET TRACEBACK*: " + tb
