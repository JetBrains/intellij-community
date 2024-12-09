#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import pytest
import sys
import _pydevd_bundle.tables.pydevd_numpy as numpy_tables_helpers
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR


def check_info_np_array(arr, file):
    actual = [numpy_tables_helpers.get_type(arr),
              NEXT_VALUE_SEPARATOR,
              numpy_tables_helpers.get_shape(arr),
              NEXT_VALUE_SEPARATOR,
              numpy_tables_helpers.get_head(arr),
              NEXT_VALUE_SEPARATOR,
              numpy_tables_helpers.get_column_types(arr)]
    actual = '\n'.join(actual)
    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file=file
    )

def _get_python_version_info():
    # () -> str
    return str(sys.version_info[0]) + '_' + str(sys.version_info[1])


def test_simple_array():
    arr = np.array([1, 2, 3])
    exp_file_python_ver = _get_python_version_info()
    check_info_np_array(arr, 'test_data/numpy/simple_np_array_' + exp_file_python_ver + '_after.txt')


def test_simple_2d_array():
    arr = np.array([[True, "False", True], [True, True, False]])
    exp_file_python_ver = _get_python_version_info()
    check_info_np_array(arr, 'test_data/numpy/simple_2d_np_array_' + exp_file_python_ver + '_after.txt')


def test_2d_array():
    arr = np.array([[1, 2, 3],
                    [4, 5, 6],
                    [7, 8, 9]])
    exp_file_python_ver = _get_python_version_info()
    check_info_np_array(arr, 'test_data/numpy/2d_np_array_' + exp_file_python_ver + '_after.txt')


def test_array_with_dtype():
    arr = np.array([(10, 3.14, 'Hello', True),
                    (20, 2.71, 'World', False)],
                   dtype=[
                       ("ci", "i4"),
                       ("cf", "f4"),
                       ("cs", "U16"),
                       ("cb", "?")])
    exp_file_python_ver = _get_python_version_info()
    check_info_np_array(arr, 'test_data/numpy/np_array_with_dtype_' + exp_file_python_ver + '_after.txt')


def test_sorting_simple_array():
    arr = np.array([1, 2, 3])
    sort_by_index = _sort_array(arr, [0], [False])
    expected_indexes = np.array([2, 1, 0])
    expected_arr = arr[::-1]
    assert _is_equals(sort_by_index.array, expected_arr)
    assert _is_equals(sort_by_index.indexes, expected_indexes)

    sort_by_values = _sort_array(arr, [1], [False])
    assert _is_equals(sort_by_values.array, expected_arr)
    assert _is_equals(sort_by_values.indexes, expected_indexes)


def test_sorting_2d_array():
    arr = np.array([[1, 2, 7],
                    [4, 2, 2],
                    [7, 8, 9]])
    sorted_array = _sort_array(arr, [2, 1], [True, False])
    expected_indexes = np.array([1, 0, 2])
    expected_arr = np.array([[4, 2, 2],
                             [1, 2, 7],
                             [7, 8, 9]])
    assert _is_equals(sorted_array.array, expected_arr)
    assert _is_equals(sorted_array.indexes, expected_indexes)


def test_sorting_array_with_types():
    dtypes = [
        ("ci", "i4"),
        ("cf", "f4"),
        ("cs", "U16"),
        ("cb", "?")]
    arr = np.array([(10, 3.14, 'a', True),
                    (20, 2.71, 'b', True),
                    (30, 2.71, 'a', True)],
                   dtype=dtypes)
    sorted_array = _sort_array(arr, [4, 3, 2], [True, True, True])
    expected_indexes = np.array([2, 0, 1])
    expected_arr = np.array([(30, 2.71, 'a', True),
                             (10, 3.14, 'a', True),
                             (20, 2.71, 'b', True)],
                            dtype=dtypes)
    assert _is_equals(sorted_array.indexes, expected_indexes)
    assert _is_equals(sorted_array.array, expected_arr)


def read_expected_from_file_and_compare_with_actual(actual, expected_file):
    with open(expected_file, 'r') as in_f:
        expected = in_f.read()


    print("expected: ", expected)
    print()
    print("actual: ", actual)
    # for a more convenient assertion fails messages here we compare string char by char
    for ind, (act, exp) in enumerate(zip(actual, expected)):
        assert act == exp, \
            ("index is %s, act part = %s,\n\nexp part = %s" %
             (ind,
#               actual[max(0, ind - 20): min(len(actual) - 1, ind + 20)],
#               expected[max(0, ind - 20): min(len(actual) - 1, ind + 20)]))
              actual,
              expected))


def _sort_array(arr, cols, orders):
    return numpy_tables_helpers._NpTable(arr).sort((cols, orders))


def _is_equals(arr_a, arr_b):
    return (arr_a == arr_b).all()
