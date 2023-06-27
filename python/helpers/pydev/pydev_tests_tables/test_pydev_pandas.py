#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""
Here we test aux methods for pandas tables handling, namely,
check functions from _pydevd_bundle.tables.pydevd_pandas module.
"""

import sys
import pytest
import pandas as pd
import _pydevd_bundle.tables.pydevd_pandas as pandas_tables_helpers
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR


@pytest.fixture
def setup_dataframe():
    """
    Here we create a fixture for tests that are related to DataFrames.
    Also, we create other auxiliary data
    """
    rows_number = 4
    max_cols = 10
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
            "datetime64[ns, <tz>]": pd.date_range("20130101", periods=rows_number, tz="CET"),
            "period": pd.Period('2012-1-1', freq='D'),
            "category": pd.Series(list("ABCD")).astype("category"),
            "interval": pd.interval_range(start=pd.Timestamp("2017-01-01"),
                                          periods=rows_number, freq="W"),
        }
    )
    df_html = repr(df.head().to_html(notebook=True, max_cols=max_cols))
    columns_types = [str(df.index.dtype)] + [str(t) for t in df.dtypes]

    return rows_number, max_cols, df, df_html, columns_types


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
    return pd.read_csv('test_data/dataframe_many_columns_before.csv')


def test_info_command(setup_dataframe):
    """
    Here we check the correctness of info command that is invoked via Kotlin.
    :param setup_dataframe: fixture/data for the test
    """
    rows, max_cols, df, df_html, cols_types_expected = setup_dataframe

    cols_types_actual = pandas_tables_helpers.get_column_types(df)
    cols_types_actual = cols_types_actual.split(pandas_tables_helpers.TABLE_TYPE_NEXT_VALUE_SEPARATOR)

    assert pandas_tables_helpers.get_type(df) == str(pd.DataFrame)
    assert pandas_tables_helpers.get_shape(df) == str(rows)
    assert pandas_tables_helpers.get_head(df, max_cols) == df_html
    assert cols_types_actual == cols_types_expected


def test_get_data_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, _, df, _, _ = setup_dataframe

    max_columns_before = pd.get_option('display.max_columns')
    max_colwidth_before = pd.get_option('display.max_colwidth')

    pandas_tables_helpers.get_data(df, max_cols=123, max_colwidth=123)

    max_columns_after = pd.get_option('display.max_columns')
    max_colwidth_after = pd.get_option('display.max_colwidth')

    assert max_columns_before == max_columns_after
    assert max_colwidth_before == max_colwidth_after


def test_display_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, _, df, _, _ = setup_dataframe

    max_columns_before = pd.get_option('display.max_columns')
    max_colwidth_before = pd.get_option('display.max_colwidth')

    pandas_tables_helpers.display_data(df,
                                       max_cols=123, max_colwidth=123,
                                       start=0, end=2)

    max_columns_after = pd.get_option('display.max_columns')
    max_colwidth_after = pd.get_option('display.max_colwidth')

    assert max_columns_before == max_columns_after
    assert max_colwidth_before == max_colwidth_after


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
    _, _, df, _, _ = setup_dataframe
    for col in df.columns:
        converted_series = pandas_tables_helpers.__convert_to_df(df[col])

        assert isinstance(converted_series, pd.DataFrame)
        assert converted_series.columns[0] == col


def test_convert_to_df_ndarray(setup_dataframe):
    """
    In this test we check two methods: __convert_to_df and __get_column_name.
    For a np.ndarray case.
    :param setup_dataframe: fixture/data for the test
    """
    _, _, df, _, _ = setup_dataframe

    for col in df.columns:
        converted_series = pandas_tables_helpers.__convert_to_df(df[col].values)

        assert isinstance(converted_series, pd.DataFrame)
        assert converted_series.columns[0] == 0


@pytest.mark.skipif(sys.version_info < (3, 0), reason="TODO: investigate pd.Categorical/complex cases")
def test_get_info_format(setup_dataframe):
    """
    We have a common format for the result for dataframe info command.
    As a reference of the format here we take getInfoCommandActions from DSTableCommands

    print(get_type(initCommandResult))
    print('$NEXT_VALUE_SEPARATOR')
    print(get_shape(initCommandResult))
    print('$NEXT_VALUE_SEPARATOR')
    print(get_head(initCommandResult, max_cols=$MAX_COLS))
    print('$NEXT_VALUE_SEPARATOR')
    print(get_column_types(initCommandResult))

    Here we check that with pandas_tables_helpers methods can compose expected result

    TODO: we also should check this format for pydevd_tables.exec_table_command
    TODO: actually, the format is different: in one case we have \n, in other just ''
    :param setup_dataframe: fixture/data for the test, dataframe
    """
    _, max_cols, df, _, _ = setup_dataframe
    actual = [pandas_tables_helpers.get_type(df),
              NEXT_VALUE_SEPARATOR,
              pandas_tables_helpers.get_shape(df),
              NEXT_VALUE_SEPARATOR,
              pandas_tables_helpers.get_head(df, max_cols),
              NEXT_VALUE_SEPARATOR,
              pandas_tables_helpers.get_column_types(df)]
    actual = '\n'.join(actual)

    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas_getInfo_result.txt'
    )


@pytest.mark.skipif(sys.version_info < (3, 0), reason="Different format for Python2")
def test_describe_many_columns_check_html(setup_dataframe_many_columns):
    df = setup_dataframe_many_columns
    actual = pandas_tables_helpers.get_column_desciptions(df, -1, 1000)

    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/dataframe_many_columns_describe_after.txt'
    )


@pytest.mark.skipif(sys.version_info < (3, 0), reason="Different format for Python2")
def test_counts_many_columns_check_html(setup_dataframe_many_columns):
    df = setup_dataframe_many_columns
    actual = pandas_tables_helpers.get_value_counts(df, -1, 1000)

    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/dataframe_many_columns_counts_after.txt'
    )


def test_describe_shape_numeric_types(setup_dataframe_many_columns):
    df = setup_dataframe_many_columns
    describe_df = pandas_tables_helpers.__get_describe_df(df)

    # for dataframes with only numeric types in columns we have 10 statistics
    assert describe_df.shape[0] == 10
    # the number of columns should be the same
    assert describe_df.shape[1] == describe_df.shape[1]


def test_counts_shape(setup_dataframe_many_columns):
    df = setup_dataframe_many_columns
    counts_df = pandas_tables_helpers.__get_counts_df(df)

    # only one row in counts_df with the number of non-NaN-s values
    assert counts_df.shape[0] == 1
    # the number of columns should be the same
    assert counts_df.shape[1] == df.shape[1]


def test_describe_shape_all_types(setup_dataframe):
    _, _, df, _, _ = setup_dataframe
    describe_df = pandas_tables_helpers.__get_describe_df(df)
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
    _, _, df, _, _ = setup_dataframe
    describe_df = pandas_tables_helpers.__get_describe_df(df)
    original_columns, describe_columns = df.columns.tolist(), describe_df.columns.tolist()

    # the number of columns is the same in described and in original
    assert len(original_columns) == len(describe_columns)

    # compare columns and it's order
    for expected, actual in zip(original_columns, describe_columns):
        assert expected == actual


def read_expected_from_file_and_compare_with_actual(actual, expected_file):
    with open(expected_file, 'r') as in_f:
        expected = in_f.read()

    # for a more convenient assertion fails messages here we compare string char by char
    for act, exp in zip(actual, expected):
        assert act == exp
