#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import pytest
from datasets import Dataset
import pandas as pd
import datetime
import sys

from io import StringIO
from IPython.display import HTML

import _pydevd_bundle.tables.pydevd_dataset as datasets_helpers
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR

DATASET_HELPERS_PATH = "_pydevd_bundle.tables.pydevd_dataset"
TYPE_BOOL, TYPE_NUMERIC, TYPE_CATEGORICAL = "bool", "numeric", "categorical"
test_data_directory = "python_" + str(sys.version_info[0]) + '_' + str(
    sys.version_info[1])


@pytest.fixture
def setup_dataset():
    """
    Here we create a fixture for tests that are related to `datasets.Dataset`.
    Also, we create other auxiliary data.
    """
    rows_number = 4
    data = {
        "A": [1.0] * rows_number,
        "B": ["foo", "bar", "baz", "qux"],
        "C": [None] * rows_number,
        "D": [1, 2, 3, 4],
        "E": [True, False, True, False],
        "dates": [datetime.datetime(2023, 1, x + 1, 0, 0) for x in range(rows_number)],
        "lists": [[1, 2], [3, 4], [5, 6], [7, 8]],
        "dicts": [{"key": "value"} for _ in range(rows_number)],
        "tuples": [("first", "second") for _ in range(rows_number)],
    }
    dataset = Dataset.from_dict(data)
    return dataset


@pytest.fixture
def setup_dataset_with_float_values():
    data = {
        "int_col": [1, 2, 3],
        "float_col": [1.0, 2.0, None],
        "strings": ["f", "s", None],
        "dict": [{"age": 30, "height": 5.5},
                 {"age": 25, "height": 6.1},
                 {"age": 35, "height": None}],
        "list": [[1.1, 2.2], [2.2, 3.3], [4.4, None]]
    }
    dataset = Dataset.from_dict(data)
    return dataset


# 1
def test_info_command(setup_dataset):
    """
    Here we check the correctness of info command that is invoked via Kotlin.
    :param setup_dataset: fixture/data for the test
    """
    dataset = setup_dataset
    __check_info_dataset(dataset,
                         'test_data/datasets/' + test_data_directory + '/dataset_simple.txt')


# 2
def test_get_data_saves_display_options(setup_dataset):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataset: fixture/data for the test
    """
    dataset = setup_dataset

    max_columns_before = pd.get_option('display.max_columns')
    max_colwidth_before = pd.get_option('display.max_colwidth')
    max_rows_before = pd.get_option('display.max_rows')

    datasets_helpers.get_data(dataset, False, format="%.2f")

    max_columns_after = pd.get_option('display.max_columns')
    max_colwidth_after = pd.get_option('display.max_colwidth')
    max_rows_after = pd.get_option('display.max_rows')

    assert max_columns_before == max_columns_after
    assert max_colwidth_before == max_colwidth_after
    assert max_rows_before == max_rows_after


# 3
def test_display_html_saves_display_options(setup_dataset):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataset: fixture/data for the test
    """
    dataset = setup_dataset

    max_columns_before = pd.get_option('display.max_columns')
    max_colwidth_before = pd.get_option('display.max_colwidth')
    max_rows_before = pd.get_option('display.max_rows')

    datasets_helpers.display_data_html(dataset, start_index=0, end_index=2)

    max_columns_after = pd.get_option('display.max_columns')
    max_colwidth_after = pd.get_option('display.max_colwidth')
    max_rows_after = pd.get_option('display.max_rows')

    assert max_columns_before == max_columns_after
    assert max_colwidth_before == max_colwidth_after
    assert max_rows_before == max_rows_after


# 4
def test_display_csv_saves_display_options(setup_dataset):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataset: fixture/data for the test
    """
    dataset = setup_dataset

    max_columns_before = pd.get_option('display.max_columns')
    max_colwidth_before = pd.get_option('display.max_colwidth')
    max_rows_before = pd.get_option('display.max_rows')

    datasets_helpers.display_data_csv(dataset, start_index=0, end_index=2)

    max_columns_after = pd.get_option('display.max_columns')
    max_colwidth_after = pd.get_option('display.max_colwidth')
    max_rows_after = pd.get_option('display.max_rows')

    assert max_columns_before == max_columns_after
    assert max_colwidth_before == max_colwidth_after
    assert max_rows_before == max_rows_after


# 5
def test_define_format_function():
    assert datasets_helpers.__define_format_function(None) is None
    assert datasets_helpers.__define_format_function('null') is None
    assert datasets_helpers.__define_format_function('garbage') is None
    assert datasets_helpers.__define_format_function(1) is None

    format_to_result = {
        "%.2f": (1.1, "1.10"),
        "%.12f": (1.1, "1.100000000000"),
        "%.2e": (1.1, "1.10e+00"),
        "%d": (1.1, "1"),
        "%d garbage": (1.1, "1 garbage"),
    }
    for format_str, (float_value, expected_result) in format_to_result.items():
        formatter = datasets_helpers.__define_format_function(format_str)
        assert formatter is not None
        assert callable(formatter)
        assert formatter(float_value) == expected_result


# 6
def test_get_tables_display_options():
    max_cols, max_colwidth, max_rows = datasets_helpers.__get_tables_display_options()
    assert max_cols is None
    assert max_rows is None
    if sys.version_info < (3, 0) or int(pd.__version__.split('.')[0]) < 1:
        assert max_colwidth == datasets_helpers.MAX_COLWIDTH
    else:
        assert max_colwidth is None


# TODO: fix issues with not none start + end indices in the tests below
# 7
def test_get_data_float_values_2f(setup_dataset_with_float_values):
    df = setup_dataset_with_float_values
    actual = datasets_helpers.get_data(df, False, 0, 5, format="%.2f")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/datasets/' + test_data_directory + '/get_data_float_values_2f.txt'
    )


# 8
def test_get_data_float_values_12f(setup_dataset_with_float_values):
    df = setup_dataset_with_float_values
    actual = datasets_helpers.get_data(df, False, 0, 5, format="%.12f")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/datasets/' + test_data_directory + '/get_data_float_values_12f.txt'
    )


def __check_info_dataset(arr, file):
    actual = [datasets_helpers.get_type(arr),
              NEXT_VALUE_SEPARATOR,
              datasets_helpers.get_shape(arr),
              NEXT_VALUE_SEPARATOR,
              datasets_helpers.get_head(arr),
              NEXT_VALUE_SEPARATOR,
              datasets_helpers.get_column_types(arr)]
    actual = '\n'.join(actual)

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file=file
    )


# 8
def test_get_data_float_values_2e(setup_dataset_with_float_values):
    df = setup_dataset_with_float_values
    actual = datasets_helpers.get_data(df, False, 0, 5, format="%.2e")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/datasets/' + test_data_directory + '/get_data_float_values_2e.txt'
    )


#9
def test_get_data_float_values_d(setup_dataset_with_float_values):
    df = setup_dataset_with_float_values
    actual = datasets_helpers.get_data(df, False, 0, 5, format="%d")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/datasets/' + test_data_directory + '/get_data_float_values_d.txt'
    )


# 10
def test_get_data_float_values_d_garbage(setup_dataset_with_float_values):
    df = setup_dataset_with_float_values
    actual = datasets_helpers.get_data(df, False, 0, 5, format="%d garbage")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/datasets/' + test_data_directory + '/get_data_float_values_d_garbage.txt'
    )


# 11
def test_display_data_html_dataset(mocker, setup_dataset):
    dataset = setup_dataset
    dataset = dataset.remove_columns(['dates'])

    # Mock the HTML and display functions
    mock_display = mocker.patch('IPython.display.display')
    datasets_helpers.display_data_html(dataset, 0, 5)
    called_args, called_kwargs = mock_display.call_args
    displayed_html = called_args[0]

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_html.data,
        expected_file='test_data/datasets/' + test_data_directory + '/display_data_html_dataset.txt'
    )


# 12 TODO: add commas to nested lists and tuples
def test_display_data_csv_dataset(mocker, setup_dataset):
    dataset = setup_dataset
    dataset = dataset.remove_columns(['dates'])

    # Mock the CSV and display functions
    mock_print = mocker.patch('sys.stdout', new_callable=StringIO)
    datasets_helpers.display_data_csv(dataset, 0, 16)
    displayed_csv = mock_print.getvalue()

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_csv,
        expected_file='test_data/datasets/' + test_data_directory + '/display_data_csv_dataset.txt'
    )


# 13
def test_display_data_html_dataset_with_float_values(mocker, setup_dataset_with_float_values):
    df = setup_dataset_with_float_values

    # Mock the HTML and display functions
    mock_display = mocker.patch('IPython.display.display')
    datasets_helpers.display_data_html(df, 0, 3)
    called_args, called_kwargs = mock_display.call_args
    displayed_html = called_args[0]

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_html.data,
        expected_file='test_data/datasets/' + test_data_directory + '/display_data_html_dataset_with_float_values.txt'
    )


# 14 TODO: add commas to nested lists and tuples
def test_display_data_csv_dataset_with_float_values(mocker, setup_dataset_with_float_values):
    df = setup_dataset_with_float_values

    # Mock the CSV and display functions
    mock_print = mocker.patch('sys.stdout', new_callable=StringIO)
    datasets_helpers.display_data_csv(df, 0, 3)
    displayed_csv = mock_print.getvalue()

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_csv,
        expected_file='test_data/datasets/' + test_data_directory + '/display_data_csv_dataset_with_float_values.txt'
    )


def __read_expected_from_file_and_compare_with_actual(actual, expected_file):
    with open(expected_file, 'r') as in_f:
        expected = in_f.read()

    assert len(expected) > 0

    # for a more convenient assertion fails messages here we compare string char by char
    for ind, (act, exp) in enumerate(zip(actual, expected)):
        assert act == exp, \
            ("index is %s \n act part = %s\n\nexp part = %s" % (ind, actual, expected))
