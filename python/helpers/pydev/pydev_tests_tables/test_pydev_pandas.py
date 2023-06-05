#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""
Here we test aux methods for pandas tables handling, namely,
check functions from _pydevd_bundle.tables.pydevd_pandas module.
"""

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
            "E": [None, "bar", 2., 1 + 10j],
            "F": [True, False] * (rows_number // 2),
            "G": pd.Timestamp("20130102"),
            "H": pd.Series(1, index=list(range(rows_number)),
                           dtype="float32"),
            "I": pd.Series(range(rows_number),
                           index=list(range(rows_number)),
                           dtype="int32"),
            # "J": pd.Categorical(["test", "train"] * (rows_number // 2)),
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
    actual_get_info_com_result = [pandas_tables_helpers.get_type(df),
                                  NEXT_VALUE_SEPARATOR,
                                  pandas_tables_helpers.get_shape(df),
                                  NEXT_VALUE_SEPARATOR,
                                  pandas_tables_helpers.get_head(df, max_cols),
                                  NEXT_VALUE_SEPARATOR,
                                  pandas_tables_helpers.get_column_types(df)]
    actual_get_info_com_result = '\n'.join(actual_get_info_com_result)

    with open('test_data/pandas_getInfo_result.txt', 'r') as in_f:
        expected_get_info_com_result = in_f.read()

    # for a more convenient assertion fails messages here we compare string char by char
    for act, exp in zip(actual_get_info_com_result, expected_get_info_com_result):
        assert act == exp
