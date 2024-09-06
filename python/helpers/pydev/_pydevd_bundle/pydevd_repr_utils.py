#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
from array import array
from collections import deque

from _pydev_bundle import pydev_log
from _pydevd_bundle.pydevd_constants import IS_PY3K
from _pydevd_bundle.pydevd_utils import take_first_n_coll_elements

# Maximum final result string length
MAX_REPR_LENGTH = 1000
# Maximum number of value elements
MAX_REPR_ITEM_SIZE = 256
DEFAULT_FORMAT = '%s'


def _get_ndarray_variable_repr(num_array, max_items=MAX_REPR_ITEM_SIZE):
    # ndarray.__str__() is already optimised and works fast enough
    if num_array.ndim == 0:
        return str(num_array).replace('\n', ',').strip()
    else:
        return str(num_array[:max_items]).replace('\n', ',').strip()


def _get_series_variable_repr(series, max_items=MAX_REPR_ITEM_SIZE):
    res = []
    total_length = 0
    series = series.iloc[:max_items]
    for item in series.items():
        # item: (index, value)
        item_repr = str(item)
        res.append(item_repr)
        total_length += len(item_repr)
        if total_length > MAX_REPR_LENGTH:
            break
    return ' '.join(res)


def _get_df_variable_repr(data_frame):
    # Avoid using df.iteritems() or df.values[i], because it works very slow for
    # large data frames. df.__str__() is already optimised and works fast enough.
    data_preview = []
    column_row = 0
    shape_rows, shape_cols = data_frame.shape
    if shape_cols > 1000 or shape_rows > 10000:
        head_number = 1
    else:
        head_number = 3
    rows = str(data_frame.head(head_number)).split('\n')
    for (i, r) in enumerate(rows):
        if i != column_row:
            data_preview.append("[%s]" % r)

        if r == '':
            column_row = i + 1

    # The string provided is used for column name completion
    # by JupyterVarsFrameExecutor.parseFrameVars
    return '%s %s' % (list(data_frame.columns), ' '.join(data_preview))


def _trim_string_repr_if_needed(value, do_trim=True, max_length=MAX_REPR_LENGTH):
    if len(value) > max_length and do_trim:
        value = value[:max_length]
        value += '...'
    return value


def _get_external_collection_repr(collection, raise_exception=False):
    typename = type(collection).__name__
    typename_with_package = type(collection)

    # pandas var
    try:
        if typename == "Series" or typename == "GeoSeries":
            return _get_series_variable_repr(collection)
        if typename == "DataFrame" or typename == "GeoDataFrame":
            return _get_df_variable_repr(collection)
    except Exception as e:
        pydev_log.warn("Failed to format pandas variable: " + str(e))
        if raise_exception:
            raise e
    # ndarray and other numpy types
    try:
        if typename == 'ndarray' or "numpy." in str(typename_with_package):
            return _get_ndarray_variable_repr(collection)
    except Exception as e:
        pydev_log.warn("Failed to format numpy ndarray: " + str(e))
        if raise_exception:
            raise e
    return None


pydevd_repr_function_python2 = None


if IS_PY3K:
    from reprlib import Repr
    from itertools import islice


    def _possibly_sorted(x):
        # Since not all sequences of items can be sorted and comparison
        # functions may raise arbitrary exceptions, return an unsorted
        # sequence in that case.
        try:
            return sorted(x)
        except Exception:
            return list(x)


    class PydevdRepr(Repr):
        def __init__(self, do_trim):
            super(PydevdRepr, self).__init__()
            self.fillvalue = '...'
            self.maxdict = \
            self.maxlist = \
            self.maxtuple = \
            self.maxset = \
            self.maxfrozenset = \
            self.maxdeque = \
            self.maxarray = \
            self.maxlong = \
            self.maxstring = \
            self.maxother = MAX_REPR_ITEM_SIZE
            self.do_trim = do_trim

        def _repr_iterable(self, x, level, left, right, maxiter, trail=''):
            n = len(x)
            if level <= 0 and n:
                s = self.fillvalue
            else:
                newlevel = level - 1
                repr1 = self.repr1
                pieces = []
                curr_length = 0
                max_elements = maxiter if self.do_trim else n
                for elem in islice(x, max_elements):
                    elem_repr = repr1(elem, newlevel)
                    curr_length += len(elem_repr)
                    pieces.append(elem_repr)
                    if curr_length >= MAX_REPR_LENGTH and self.do_trim:
                        break

                if (n > maxiter or curr_length >= MAX_REPR_LENGTH) and self.do_trim:
                    pieces.append(self.fillvalue)
                s = ', '.join(pieces)
                if n == 1 and trail:
                    right = trail + right
            return '%s%s%s' % (left, s, right)

        def repr_str(self, x, level):
            if level == self.maxlevel:
                if self.do_trim:
                    return x[:self.maxstring]
                else:
                    return x
            return super().repr_str(x, level)

        def repr_dict(self, x, level):
            n = len(x)
            if n == 0: return '{}'
            if level <= 0: return '{...}'
            newlevel = level - 1
            repr1 = self.repr1
            pieces = []
            curr_length = 0
            max_elements = self.maxdict if self.do_trim else n
            for key in islice(_possibly_sorted(x), max_elements):
                keyrepr = repr1(key, newlevel)
                valrepr = repr1(x[key], newlevel)
                elem_repr = '%s: %s' % (keyrepr, valrepr)
                pieces.append(elem_repr)
                curr_length += len(elem_repr)
                if curr_length >= MAX_REPR_LENGTH and self.do_trim:
                    break

            if (n > self.maxdict or curr_length >= MAX_REPR_LENGTH) and self.do_trim:
                pieces.append(self.fillvalue)
            s = ', '.join(pieces)
            return '{%s}' % (s,)

        def repr_instance(self, x, level):
            # pandas series, ds | ndarray
            result = _get_external_collection_repr(x)
            if result is not None:
                return result

            # if `__repr__` is overridden, then use `reprlib`
            if x.__class__.__repr__ != object.__repr__:
                return super().repr_instance(x, level)

            # if `__str__` is overridden, then return str(x)
            if x.__class__.__str__ != object.__str__:
                return str(x)

            # else use `reprlib`
            return super().repr_instance(x, level)

else:
    def pydevd_repr_function(value, do_trim=True):
        # pandas series, ds | ndarray
        result = _get_external_collection_repr(value, True)
        if result is not None:
            return result

        limited_size_collection_classes = [
            list, tuple, set, frozenset, dict, array, deque, str,
        ]

        if IS_PY3K:
            limited_size_collection_classes.append(bytes)
        else:
            limited_size_collection_classes.append(unicode)

        if hasattr(value, '__class__'):
            if value.__class__ in limited_size_collection_classes:
                if len(value) > MAX_REPR_ITEM_SIZE and do_trim:
                    return ('%s' % take_first_n_coll_elements(value, MAX_REPR_ITEM_SIZE)).rstrip(')]}') + '...'
                return None

        # if `__repr__` is overridden, then return repr(value)
        if hasattr(value.__class__, "__repr__"):
            if do_trim:
                return repr(value)[:MAX_REPR_LENGTH]
            else:
                return repr(value)

        # else
        if do_trim:
            return str(value)[:MAX_REPR_LENGTH]
        else:
            return str(value)

    pydevd_repr_function_python2 = pydevd_repr_function


def get_value_repr(value, do_trim=True, format=DEFAULT_FORMAT):
    """
    Returns string representation of any value

    :param value: target value
    :param bool do_trim: is truncated representation
    :param str format: formatting string (format % value)
    :return: string representation of target value
    :rtype: str
    """
    value_representation = None
    try:
        try:
            if format != DEFAULT_FORMAT:
                value_representation = format % value
            else:
                if IS_PY3K:
                    pydevd_repr_fun = PydevdRepr(do_trim).repr
                    value_representation = pydevd_repr_fun(value)
                else:
                    value_representation = pydevd_repr_function_python2(value, do_trim)

        except Exception as e:
            pydev_log.warn("Failed to get repr for a value: " + str(e))

        if value_representation is None:
            value_representation = format % value

        if do_trim:
            return _trim_string_repr_if_needed(value_representation, do_trim)
        else:
            return value_representation
    except:
        try:
            return _trim_string_repr_if_needed(repr(value), do_trim)
        except:
            return 'Unable to get repr for %s' % value.__class__
