#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""
Here we test aux methods for pandas tables handling, namely,
check functions from _pydevd_bundle.tables.pydevd_pandas module.
"""

import pandas as pd
import pytest
import sys

import _pydevd_bundle.tables.pydevd_pandas as pandas_tables_helpers
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR


@pytest.fixture
def setup_dataframe():
    """
    Here we create a fixture for tests that are related to DataFrames.
    Also, we create other auxiliary data
    """
    rows_number = 4
    df = pd.DataFrame(
        {
            "A": 1.0,
            "B": "foo",
            "C": [None] * rows_number,
            "D": [1 + 20j] * rows_number,
            "E": [1 + 20j] * rows_number,
            "F": [None, "bar", 2., 1 + 10j],
            "G": [None, "bar", 2., 1 + 10j],
            "H": [True, False] * (rows_number // 2),
            "I": pd.Timestamp("20130102"),
            "J": pd.Series(1, index=list(range(rows_number)),
                           dtype="float32"),
            "K": pd.Series(range(rows_number),
                           index=list(range(rows_number)),
                           dtype="int32"),
            "L": pd.Categorical(["test", "train"] * (rows_number // 2)),
            "dates": pd.date_range("now", periods=rows_number),
            "datetime64[ns]": pd.Timestamp("20010102"),
            "datetime64[ns, <tz>]": pd.date_range("20130101", periods=rows_number,
                                                  tz="CET"),
            "period": pd.Period('2012-1-1', freq='D'),
            "category": pd.Series(list("ABCD")).astype("category"),
            "interval": pd.interval_range(start=pd.Timestamp("2017-01-01"),
                                          periods=rows_number, freq="W"),
        }
    )
    df['datetime64[ns]'] = df['datetime64[ns]'].astype("datetime64[ns]")
    df['I'] = df['I'].astype("datetime64[ns]")
    df_html = repr(df.head(1).to_html(notebook=True, max_cols=None))
    columns_types = [str(df.index.dtype)] + [str(t) for t in df.dtypes]

    return rows_number, df, df_html, columns_types


@pytest.fixture
def setup_series_no_names():
    """
    Here we create a fixture for tests that are related to Series without a name.
    """
    return pd.Series([1, 2, 3])


@pytest.fixture
def setup_dataframe_many_columns():
    """
    Here we create a fixture for tests that are related to DataFrames.
    We check that we don't miss columns for big dataframes
    """
    return pd.read_csv('test_data/pandas/dataframe_many_columns_before.csv')


@pytest.fixture
def setup_df_with_big_int_values():
    """
    Here we create a fixture for one test.
    With that df we check that we catch OverflowError exception in the describe functions.
    This number has to be so big.
    """
    big_int = 555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555
    df = pd.DataFrame({"BitIntValues": [1, 2]})
    df["BitIntValues"] = big_int

    return df


def test_info_command(setup_dataframe):
    """
    Here we check the correctness of info command that is invoked via Kotlin.
    :param setup_dataframe: fixture/data for the test
    """
    rows, df, df_html, cols_types_expected = setup_dataframe

    cols_types_actual = pandas_tables_helpers.get_column_types(df)
    cols_types_actual = cols_types_actual.split(pandas_tables_helpers.TABLE_TYPE_NEXT_VALUE_SEPARATOR)

    assert pandas_tables_helpers.get_type(df) == str(pd.DataFrame)
    assert pandas_tables_helpers.get_shape(df) == str(rows)
    assert pandas_tables_helpers.get_head(df) == df_html
    assert cols_types_actual == cols_types_expected


def test_get_data_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _ = setup_dataframe

    max_columns_before = pd.get_option('display.max_columns')
    max_colwidth_before = pd.get_option('display.max_colwidth')
    max_rows_before = pd.get_option('display.max_rows')

    pandas_tables_helpers.get_data(df, False)

    max_columns_after = pd.get_option('display.max_columns')
    max_colwidth_after = pd.get_option('display.max_colwidth')
    max_rows_after = pd.get_option('display.max_rows')

    assert max_columns_before == max_columns_after
    assert max_colwidth_before == max_colwidth_after
    assert max_rows_before == max_rows_after


def test_display_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _ = setup_dataframe

    max_columns_before = pd.get_option('display.max_columns')
    max_colwidth_before = pd.get_option('display.max_colwidth')
    max_rows_before = pd.get_option('display.max_rows')

    pandas_tables_helpers.display_data_html(df, start_index=0, end_index=2)

    max_columns_after = pd.get_option('display.max_columns')
    max_colwidth_after = pd.get_option('display.max_colwidth')
    max_rows_after = pd.get_option('display.max_rows')

    assert max_columns_before == max_columns_after
    assert max_colwidth_before == max_colwidth_after
    assert max_rows_before == max_rows_after


def test_convert_to_df_unnamed_series(setup_series_no_names):
    """
    In this test we check two methods: __convert_to_df and __get_column_name.
    For unnamed pd.Series case.
    :param setup_series_no_names: fixture/data for the test
    """
    converted_series = pandas_tables_helpers.__convert_to_df(setup_series_no_names)

    assert isinstance(converted_series, pd.DataFrame)
    assert converted_series.columns[0] == '<unnamed>'


def test_convert_to_df_common_series(setup_dataframe):
    """
    In this test we check two methods: __convert_to_df and __get_column_name.
    For a common pd.Series case.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _ = setup_dataframe
    for col in df.columns:
        converted_series = pandas_tables_helpers.__convert_to_df(df[col])

        assert isinstance(converted_series, pd.DataFrame)
        assert converted_series.columns[0] == col


@pytest.mark.skipif(sys.version_info < (3, 0),
                    reason="TODO: investigate pd.Categorical/complex cases")
def test_get_info_format(setup_dataframe):
    """
    We have a common format for the result for dataframe info command.
    As a reference of the format here we take getInfoCommandActions from DSTableCommands

    print(get_type(initCommandResult))
    print('$NEXT_VALUE_SEPARATOR')
    print(get_shape(initCommandResult))
    print('$NEXT_VALUE_SEPARATOR')
    print(get_head(initCommandResult))
    print('$NEXT_VALUE_SEPARATOR')
    print('$NEXT_VALUE_SEPARATOR')
    print(get_column_types(initCommandResult))

    Here we check that with pandas_tables_helpers methods can compose expected result

    TODO: we also should check this format for pydevd_tables.exec_table_command
    TODO: actually, the format is different: in one case we have \n, in other just ''
    :param setup_dataframe: fixture/data for the test, dataframe
    """
    _, df, _, _ = setup_dataframe

    # remove "dates" column from df because it uses "now" timestamp for data generating
    df = df.drop(columns=['dates', 'interval'])

    actual = [pandas_tables_helpers.get_type(df),
              NEXT_VALUE_SEPARATOR,
              pandas_tables_helpers.get_shape(df),
              NEXT_VALUE_SEPARATOR,
              pandas_tables_helpers.get_head(df),
              NEXT_VALUE_SEPARATOR,
              pandas_tables_helpers.get_column_types(df)]
    actual = '\n'.join(actual)

    print("GET INFO: START")
    print()
    pandas_tables_helpers.get_head(df)
    print()
    print("GET INFO: END")
    print()

    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/getInfo_result.txt'
    )


@pytest.mark.skipif(sys.version_info < (3, 0), reason="Different format for Python2")
def test_describe_many_columns_check_html(setup_dataframe_many_columns):
    df = setup_dataframe_many_columns
    actual = pandas_tables_helpers.get_column_descriptions(df)

    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/dataframe_many_columns_describe_after.txt'
    )


def test_describe_shape_numeric_types(setup_dataframe_many_columns):
    df = setup_dataframe_many_columns
    describe_df = pandas_tables_helpers.__get_describe(df)

    # for dataframes with only numeric types in columns we have 10 statistics
    assert describe_df.shape[0] == 10
    # the number of columns should be the same
    assert describe_df.shape[1] == describe_df.shape[1]


def test_describe_shape_all_types(setup_dataframe):
    _, df, _, _ = setup_dataframe
    describe_df = pandas_tables_helpers.__get_describe(df)
    # for dataframes with different types in columns we have 13/15 statistics
    if sys.version_info < (3, 0):
        # python2 have 2 additional statistics that we don't use: first and last
        assert describe_df.shape[0] == 15
    else:
        assert describe_df.shape[0] == 13
    # the number of columns should be the same
    assert describe_df.shape[1] == df.shape[1]
    # check that we excluded only 2 columns from describe
    assert len(describe_df.columns[describe_df.isna().all()].tolist()) == 2


def test_get_describe_save_columns(setup_dataframe):
    _, df, _, _ = setup_dataframe
    describe_df = pandas_tables_helpers.__get_describe(df)
    original_columns, describe_columns = df.columns.tolist(), describe_df.columns.tolist()

    # the number of columns is the same in described and in original
    assert len(original_columns) == len(describe_columns)

    # compare columns and it's order
    for expected, actual in zip(original_columns, describe_columns):
        assert expected == actual


def test_get_describe_returned_types(setup_dataframe):
    _, df, _, _ = setup_dataframe

    assert type(pandas_tables_helpers.__get_describe(df)) == pd.DataFrame
    assert type(pandas_tables_helpers.__get_describe(df['A'])) == pd.Series


@pytest.mark.skipif(sys.version_info < (3, 0), reason="Different format for Python2")
def test_describe_series(setup_dataframe):
    _, df, _, _ = setup_dataframe

    resulted = ""

    for column in df:
        # we skip dates column because its data every time is different
        if column != 'dates' and column != 'interval':
            described_series = pandas_tables_helpers.__get_describe(df[column])
            resulted += __prepare_describe_result(str(described_series)) + "\n"

    exp_file_python_ver = str(sys.version_info[0]) + '_' + str(sys.version_info[1])

    read_expected_from_file_and_compare_with_actual(
        actual=resulted,
        expected_file='test_data/pandas/series_describe_' + exp_file_python_ver + '.txt'
    )


def __prepare_describe_result(described_str):
    """
    This function is needed with the aim not to be depended on the python version,
    there is different indentation in different python versions.
    We check only the data, not the indentation.
    """
    # type: (str) -> (str)
    result = []
    for line in described_str.split("\n"):
        result.append(" ".join(line.split()))

    return "\n".join(result)


@pytest.mark.skipif(sys.version_info < (3, 0),
                    reason="The exception will be raised during df creation in Python2")
def test_overflow_error_is_caught(setup_df_with_big_int_values):
    df = setup_df_with_big_int_values
    assert pandas_tables_helpers.__get_describe(df) is None


def test_vis_data_integer_columns_simple():
    test_data = pd.DataFrame({"ints": list(range(10)) + list(range(10))})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data)
    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_integer_simple.txt'
    )


@pytest.mark.skipif(sys.version_info < (3, 0),reason="")
def test_vis_data_integer_columns_with_bins():
    test_data = pd.DataFrame({"ints": list(range(21)) + list(range(21))})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data)
    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_integer_with_bins.txt'
    )


@pytest.mark.skipif(sys.version_info < (3, 0),reason="")
def test_vis_data_float_columns_simple():
    import numpy as np
    test_data = pd.DataFrame({"floats": np.arange(0, 1, 0.1)})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data)
    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_float_simple.txt'
    )


@pytest.mark.skipif(sys.version_info < (3, 0),reason="")
def test_vis_data_float_columns_with_bins():
    import numpy as np
    test_data = pd.DataFrame({"floats": np.arange(0, 3, 0.1)})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data)
    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_float_with_bins.txt'
    )


def read_expected_from_file_and_compare_with_actual(actual, expected_file):
    with open(expected_file, 'r') as in_f:
        expected = in_f.read()

    # for a more convenient assertion fails messages here we compare string char by char
    for ind, (act, exp) in enumerate(zip(actual, expected)):
        assert act == exp, "index is %s, act part = %s, exp part = %s" % (ind,
            actual[max(0, ind - 20): min(len(actual) - 1, ind + 20)],
            expected[max(0, ind - 20): min(len(actual) - 1, ind + 20)])
