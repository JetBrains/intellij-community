import array
import collections
import sys

import numpy as np
import pandas as pd


def create_string():
    res = ""
    for i in range(97, 123):
        res += (chr(i) * i)
    return res


def create_integer():
    return sys.maxsize


def create_numpy_array():
    return np.arange(1000)


def create_pandas_df():
    np_array = np.arange(100).reshape((10, 10))
    col = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j']
    ind = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10']
    return pd.DataFrame(np_array, columns=col, index=ind)


def create_array():
    return array.array('l', range(10_000_000))


def create_instance(is_hashable=True):
    class Test:
        def __init__(self, is_hashable):
            self.str_val = create_string()
            self.int_val = create_integer()
            if is_hashable:
                self.array_val = create_array()
                self.numpy_val = create_numpy_array()
                self.pandas_val = create_pandas_df()

    return Test(is_hashable)


def _create_all_elems(is_hashable=True):
    return create_integer(), \
        create_string(), \
        create_array(), \
        create_numpy_array(), \
        create_pandas_df(), \
        create_instance(is_hashable)


def create_tuple(is_hashable=True):
    res = ()
    integer, string, arr, numpy_arr, df, instance = _create_all_elems(is_hashable)
    for _ in range(1000):
        res += (integer,)
        res += (string,)
        if is_hashable:
            res += (arr,)
            res += (numpy_arr,)
            res += (df,)
        res += (instance,)
    return res


def create_list():
    res = []
    integer, string, arr, numpy_arr, df, instance = _create_all_elems()
    for _ in range(1000):
        res.append(integer)
        res.append(string)
        res.append(arr)
        res.append(numpy_arr)
        res.append(df)
        res.append(instance)
    return res


def create_set():
    return {create_integer(), create_string(), create_tuple(False),
            create_instance(False)}


def create_frozenset():
    return frozenset(create_set())


def create_deque():
    return collections.deque(create_tuple())


def create_dict():
    res = dict()
    for i, elem in enumerate(create_tuple()):
        res[str(i)] = elem
    return res
