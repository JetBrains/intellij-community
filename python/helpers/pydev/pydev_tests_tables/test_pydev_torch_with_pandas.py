#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import pytest
import torch
import sys

from io import StringIO
from IPython.display import HTML

import _pydevd_bundle.tables.pydevd_numpy_based as numpy_based_tables_helpers
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR

test_data_directory = "python_" + str(sys.version_info[0]) + '_' + str(sys.version_info[1])


@pytest.fixture
def setup_torch_tensor_with_floats():
    tensor_with_floats = torch.tensor([
        [1, 1.1, 1.001],
        [2, 2.2, 2.002],
        [3, 3.3, 3.0003],
        [4, 4.4, 4.00004],
        [5, 5.5, 5.000005]])

    return tensor_with_floats


@pytest.fixture
def setup_torch_tensor_with_nones():
    tensor_with_nones = torch.tensor([
        [1, 1.1],
        [2, 2.2],
        [3, float('nan')],
        [4, float('nan')],
        [5, float('nan')]])

    return tensor_with_nones


# 1
def test_tensor_1d_number():
    tensor = torch.tensor([1, 2, 3])
    __check_info_torch_tensor(tensor, 'test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/torch_1d_number.txt')


# 2
def test_tensor_3d_simple():
    tensor = torch.tensor([[True, False, True], [True, True, False], [True, True, False]])
    __check_info_torch_tensor(tensor, 'test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/torch_3d_simple.txt')


# 3
def test_tensor_3d_number():
    tensor = torch.tensor([[1, 2, 3], [4, 5, 6], [7, 8, 9]])
    __check_info_torch_tensor(tensor, 'test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/torch_3d_number.txt')


# 4
def test_tensor_with_dtype():
    tensor = torch.tensor([[1, 2, 3],
                           [4, 5, 6]],
                          dtype=torch.float32)
    __check_info_torch_tensor(tensor, 'test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/torch_with_dtype.txt')


# 5
def test_define_format_function():
    assert numpy_based_tables_helpers.__define_format_function(None) is None
    assert numpy_based_tables_helpers.__define_format_function('null') is None
    assert numpy_based_tables_helpers.__define_format_function('garbage') is None
    assert numpy_based_tables_helpers.__define_format_function(1) is None

    format_to_result = {
        "%.2f": (1.1, "1.10"),
        "%.12f": (1.1, "1.100000000000"),
        "%.2e": (1.1, "1.10e+00"),
        "%d": (1.1, "1"),
        "%d garbage": (1.1, "1 garbage"),
    }

    for format_str, (float_value, expected_result) in format_to_result.items():
        formatter = numpy_based_tables_helpers.__define_format_function(format_str)
        assert formatter is not None
        assert callable(formatter)
        assert formatter(float_value) == expected_result


def __check_info_torch_tensor(arr, file):
    actual = [numpy_based_tables_helpers.get_type(arr),
              NEXT_VALUE_SEPARATOR,
              numpy_based_tables_helpers.get_shape(arr),
              NEXT_VALUE_SEPARATOR,
              numpy_based_tables_helpers.get_head(arr),
              NEXT_VALUE_SEPARATOR,
              numpy_based_tables_helpers.get_column_types(arr)]
    actual = '\n'.join(actual)

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file=file
    )


# 6
def test_get_tables_display_options():
    max_cols, max_colwidth, max_rows = numpy_based_tables_helpers.__get_tables_display_options()
    assert max_cols is None
    assert max_rows is None
    if sys.version_info < (3, 0):
        assert max_colwidth == numpy_based_tables_helpers.MAX_COLWIDTH
    else:
        assert max_colwidth is None


# 7
def test_get_data_float_values_2f(setup_torch_tensor_with_floats):
    tensor = setup_torch_tensor_with_floats
    actual = numpy_based_tables_helpers.get_data(tensor, False, 0, 5, format="%.2f")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/get_data_float_values_2f.txt'
    )


# 8 TODO: try to fix precision troubles
# def test_get_data_float_values_12f(setup_torch_tensor_with_floats):
#     np_array = setup_torch_tensor_with_floats
#     actual = numpy_based_tables_helpers.get_data(np_array, False, 0, 5, format="%.12f")
#
#     __read_expected_from_file_and_compare_with_actual(
#         actual=actual,
#         expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/get_data_float_values_12f.txt'
#     )


# 9
def test_get_data_float_values_2e(setup_torch_tensor_with_floats):
    tensor = setup_torch_tensor_with_floats
    actual = numpy_based_tables_helpers.get_data(tensor, False, 0, 5, format="%.2e")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/get_data_float_values_2e.txt'
    )


# 10
def test_get_data_float_values_d(setup_torch_tensor_with_floats):
    tensor = setup_torch_tensor_with_floats
    actual = numpy_based_tables_helpers.get_data(tensor, False, 0, 5, format="%d")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/get_data_float_values_d.txt'
    )


# 11
def test_get_data_float_values_d_garbage(setup_torch_tensor_with_floats):
    tensor = setup_torch_tensor_with_floats
    actual = numpy_based_tables_helpers.get_data(tensor, False, 0, 5, format="%d garbage")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/get_data_float_values_d_garbage.txt'
    )


# 12
def test_get_data_none_values_2e(setup_torch_tensor_with_nones):
    tensor = setup_torch_tensor_with_nones
    actual = numpy_based_tables_helpers.get_data(tensor, False, 0, 5, format="%.2e")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/get_data_none_values_2e.txt'
    )


# 13 TODO: round by default here -- fix?
def test_display_data_html_float_values(mocker, setup_torch_tensor_with_floats):
    tensor = setup_torch_tensor_with_floats

    # Mock the HTML and display functions
    mock_display = mocker.patch('IPython.display.display')
    numpy_based_tables_helpers.display_data_html(tensor, 0, 3)
    called_args, called_kwargs = mock_display.call_args
    displayed_html = called_args[0]

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_html.data,
        expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/display_data_html_float_values.txt'
    )


# 14 TODO: round by default here -- fix?
def test_display_data_html_none_values(mocker, setup_torch_tensor_with_nones):
    tensor = setup_torch_tensor_with_nones

    # Mock the HTML and display functions
    mock_display = mocker.patch('IPython.display.display')
    numpy_based_tables_helpers.display_data_html(tensor, 0, 3)
    called_args, called_kwargs = mock_display.call_args
    displayed_html = called_args[0]

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_html.data,
        expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/display_data_html_none_values.txt'
    )


# 15 TODO: round by default here -- fix?
def test_display_data_csv_float_values(mocker, setup_torch_tensor_with_floats):
    torch_tensor = setup_torch_tensor_with_floats

    # Mock the CSV and display functions
    mock_print = mocker.patch('sys.stdout', new_callable=StringIO)
    numpy_based_tables_helpers.display_data_csv(torch_tensor, 0, 3)
    displayed_csv = mock_print.getvalue()

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_csv,
        expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/display_data_csv_float_values.txt'
    )


# 16 TODO: round by default here -- fix?
def test_display_data_csv_none_values(mocker, setup_torch_tensor_with_nones):
    torch_tensor = setup_torch_tensor_with_nones

    # Mock the CSV and display functions
    mock_print = mocker.patch('sys.stdout', new_callable=StringIO)
    numpy_based_tables_helpers.display_data_csv(torch_tensor, 0, 3)
    displayed_csv = mock_print.getvalue()

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_csv,
        expected_file='test_data/numpy_based_with_pandas/torch_with_pandas/' + test_data_directory + '/display_data_csv_none_values.txt'
    )


def __read_expected_from_file_and_compare_with_actual(actual, expected_file):
    with open(expected_file, 'r') as in_f:
        expected = in_f.read()

    assert len(expected) > 0

    # for a more convenient assertion fails messages here we compare string char by char
    for ind, (act, exp) in enumerate(zip(actual, expected)):
        assert act == exp, \
            ("index is %s \n act part = %s\n\nexp part = %s" % (ind, actual, expected))
