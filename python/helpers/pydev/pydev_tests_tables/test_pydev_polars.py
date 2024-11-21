#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""
Here we test aux methods for polars tables handling, namely,
check functions from _pydevd_bundle.tables.pydevd_polars module.
"""

import pytest
import sys

import polars as pl
import numpy as np

from datetime import datetime, date, time

import _pydevd_bundle.tables.pydevd_polars as polars_tables_helpers
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR
TYPE_BOOL, TYPE_NUMERIC, TYPE_CATEGORICAL = "bool", "numeric", "categorical"


@pytest.fixture
def setup_dataframe():
    """
    Here we create a fixture for tests that are related to DataFrames.
    We create a DataFrame with various data types.
    Also, we create other auxiliary data
    """
    df = pl.DataFrame({
        "int_col": [1, 2, 3],
        "float_col": [1.0, 2.0, 3.0],
        "bool_col": [True, False, True],
        "bool_col_with_none": [True, False, None],
        "str_col": ["one", "two", "three"],
        "date_col": [date(2022, 1, 1), date(2022, 1, 2), date(2022, 1, 3)],
        "datetime_col": [datetime(2022, 1, 1, 12, 0), datetime(2022, 1, 2, 12, 0), datetime(2022, 1, 3, 12, 0)],
        "time_col": [time(12, 0), time(12, 30), time(13, 0)],
        "duration_col": [pl.duration(milliseconds=500), pl.duration(milliseconds=1000), pl.duration(milliseconds=1500)],
        "categorical_col": ["A", "B", "A"],
        "binary_col": [b"abc", b"def", b"ghi"],
        "struct_col": [{"age": 30, "height": 5.5}, {"age": 25, "height": 6.1}, {"age": 35, "height": 5.9}],
        "list_col": [[1, 2], [3, 4], [5, 6]],
        "large_number": [18446744073709551610, 18446744073709551611, 18446744073709551612]
    }, schema={"int_col": pl.Int64,
               "float_col": pl.Float64,
               "bool_col": pl.Boolean,
               "bool_col_with_none": pl.Boolean,
               "str_col": pl.String,
               "date_col": pl.Date,
               "datetime_col": pl.Datetime,
               "time_col": pl.Time,
               "duration_col": pl.Object,
               "categorical_col": pl.Categorical,
               "binary_col": pl.Binary,
               "struct_col": pl.Struct,
               "list_col": pl.List,
               "large_number": pl.UInt64
    })

    df_html = df.head(1)._repr_html_()
    columns_types = [str(t) for t in df.dtypes]
    col_name_to_data_type = {
        "int_col": TYPE_NUMERIC,
        "float_col": TYPE_NUMERIC,
        "bool_col": TYPE_BOOL,
        "bool_col_with_none": TYPE_CATEGORICAL,
        "str_col": TYPE_CATEGORICAL,
        "date_col": TYPE_CATEGORICAL,
        "datetime_col": TYPE_CATEGORICAL,
        "time_col": TYPE_CATEGORICAL,
        "duration_col": TYPE_CATEGORICAL,
        "categorical_col": TYPE_CATEGORICAL,
        "binary_col": TYPE_CATEGORICAL,
        "struct_col": TYPE_CATEGORICAL,
        "list_col": TYPE_CATEGORICAL,
        "large_number": TYPE_NUMERIC
    }


    return df.shape, df, df_html, columns_types, col_name_to_data_type


@pytest.fixture
def setup_series_empty():
    """
    Here we create a fixture for tests that are related to an empty Series.
    """
    return pl.Series(name="empty_col", values=[], dtype=pl.Int64)


@pytest.fixture
def setup_series_unnamed():
    """
    Here we create a fixture for tests that are related to a Series without a name.
    """
    return pl.Series(values=[], dtype=pl.Int64)


@pytest.fixture
def setup_dataframe_many_numeric_columns():
    """
    Here we create a fixture for tests that are related to DataFrames.
    We check that we don't miss columns for big dataframes
    """
    df_numeric = pl.DataFrame({'Col %s' % col: np.arange(1000)
                       for col in range(1, 20)})

    return df_numeric


# @pytest.fixture
# def setup_df_with_big_int_values():
#     """
#     Here we create a fixture for one test.
#     With that df we check that we catch OverflowError exception in the describe functions.
#     This number has to be so big.
#     """
#     big_int = 555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555555
#     df = pd.DataFrame({"BitIntValues": [1, 2]})
#     df["BitIntValues"] = big_int
#
#     return df


# 1
def test_info_command(setup_dataframe):
    """
    Here we check the correctness of info command that is invoked via Kotlin.
    :param setup_dataframe: fixture/data for the test
    """
    df_shape, df, df_html, cols_types_expected, _ = setup_dataframe

    cols_types_actual = polars_tables_helpers.get_column_types(df)
    cols_types_actual = cols_types_actual.split(polars_tables_helpers.TABLE_TYPE_NEXT_VALUE_SEPARATOR)

    assert polars_tables_helpers.get_type(df) == str(pl.DataFrame)
    assert polars_tables_helpers.get_shape(df) == str(df_shape)
    assert polars_tables_helpers.get_head(df) == df_html
    assert cols_types_actual == cols_types_expected


# 2
def test_get_data_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _, _ = setup_dataframe

    before_config_state = pl.Config.state()

    polars_tables_helpers.get_data(df, False)

    after_config_state = pl.Config.state()

    assert len(before_config_state.keys()) == len(after_config_state.keys())
    for key in before_config_state.keys():
        assert before_config_state[key] == after_config_state[key], \
        f"For key {key} config state after getting data has been changed: before={before_config_state[key]}, after={after_config_state[key]}"


# 3
def test_display_data_html_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _, _ = setup_dataframe

    before_config_state = pl.Config.state()

    polars_tables_helpers.display_data_html(df, start=0, end=2)

    after_config_state = pl.Config.state()

    assert len(before_config_state.keys()) == len(after_config_state.keys())
    for key in before_config_state.keys():
        assert before_config_state[key] == after_config_state[key], \
        f"For key {key} config state after getting data has been changed: before={before_config_state[key]}, after={after_config_state[key]}"


# 4
def test_display_data_csv_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _, _ = setup_dataframe

    before_config_state = pl.Config.state()

    polars_tables_helpers.display_data_csv(df, start=0, end=2)

    after_config_state = pl.Config.state()

    assert len(before_config_state.keys()) == len(after_config_state.keys())
    for key in before_config_state.keys():
        assert before_config_state[key] == after_config_state[key], \
        f"For key {key} config state after getting data has been changed: before={before_config_state[key]}, after={after_config_state[key]}"


# def test_convert_to_df_unnamed_series(setup_series_no_names):
#     """
#     In this test we check two methods: __convert_to_df and __get_column_name.
#     For unnamed pd.Series case.
#     :param setup_series_no_names: fixture/data for the test
#     """
#     converted_series = polars_tables_helpers.__convert_to_df(setup_series_no_names)
#
#     assert isinstance(converted_series, pd.DataFrame)
#     assert converted_series.columns[0] == '<unnamed>'


# 5
def test_get_df_slice(setup_dataframe):
    _, df, _, _, _ = setup_dataframe
    for col in df.columns:
        converted_series = polars_tables_helpers.__get_df_slice(df[col], 0, 1)

        assert isinstance(converted_series, pl.DataFrame)
        assert converted_series.columns[0] == col

# 6
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
    print(get_column_types(initCommandResult))

    Here we check that with polars_tables_helpers methods can compose expected result

    TODO: we also should check this format for pydevd_tables.exec_table_command
    TODO: actually, the format is different: in one case we have \n, in other just ''
    :param setup_dataframe: fixture/data for the test, dataframe
    """
    _, df, _, _, _ = setup_dataframe

    actual = [polars_tables_helpers.get_type(df),
              NEXT_VALUE_SEPARATOR,
              polars_tables_helpers.get_shape(df),
              NEXT_VALUE_SEPARATOR,
              polars_tables_helpers.get_head(df),
              NEXT_VALUE_SEPARATOR,
              polars_tables_helpers.get_column_types(df)]
    actual = '\n'.join(actual)

    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/polars/get_info_result.txt'
    )


# 7
def test_describe_many_numerical_columns_check_html(setup_dataframe_many_numeric_columns):
    df = setup_dataframe_many_numeric_columns
    actual = polars_tables_helpers.get_column_descriptions(df)

    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/polars/dataframe_many_numeric_columns_describe_after.txt'
    )

# 8
def test_describe_shape_numeric_types(setup_dataframe_many_numeric_columns):
    df = setup_dataframe_many_numeric_columns
    describe_df = polars_tables_helpers.__get_describe(df)

    # for dataframes with only numeric types in columns we have 10 statistics
    assert describe_df.shape[0] == 11
    # the number of columns should be the same plus 1 for a column with statistics names
    assert describe_df.shape[1] == df.shape[1] + 1


# 9
def test_describe_shape_all_types(setup_dataframe):
    _, df, _, _, _ = setup_dataframe
    describe_df = polars_tables_helpers.__get_describe(df)
    assert describe_df.shape[0] == 11
    # the number of columns should be the same plus 1 for a column with statistics names
    assert describe_df.shape[1] == df.shape[1] + 1


# 10
def test_get_describe_save_columns(setup_dataframe):
    _, df, _, _, _ = setup_dataframe
    describe_df = polars_tables_helpers.__get_describe(df)
    original_columns, describe_columns = df.columns, describe_df.columns

    # the number of columns is the same in described and in (original + 1)
    assert len(original_columns) + 1 == len(describe_columns)

    describe_columns = describe_columns[1:]
    # compare columns and it's order
    for expected, actual in zip(original_columns, describe_columns):
        assert expected == actual

# 11
def test_get_describe_returned_types(setup_dataframe):
    _, df, _, _, _ = setup_dataframe

    assert type(polars_tables_helpers.__get_describe(df)) == pl.DataFrame
    assert type(polars_tables_helpers.__get_describe(df['int_col'])) == pl.DataFrame

# 12
def test_describe_series(setup_dataframe):
    _, df, _, _, _ = setup_dataframe

    resulted = ""

    for column in df:
        described_series = polars_tables_helpers.__get_describe(column)
        resulted += str(described_series.to_dict(as_series=False)) + "\n"

    read_expected_from_file_and_compare_with_actual(
        actual=resulted,
        expected_file='test_data/polars/series_describe.txt'
    )


# 13
def test_vis_data_detecting_column_type(setup_dataframe):
    _, df, _, _, col_name_to_data_type = setup_dataframe
    for column in df:
        col_type = column.dtype
        if col_name_to_data_type[column.name] == TYPE_BOOL:
            assert polars_tables_helpers.__is_boolean(column, col_type) == True
            assert polars_tables_helpers.__is_numeric(column, col_type) == False
        elif col_name_to_data_type[column.name] == TYPE_NUMERIC:
            assert polars_tables_helpers.__is_boolean(column, col_type) == False
            assert polars_tables_helpers.__is_numeric(column, col_type) == True
        elif col_name_to_data_type[column.name] == TYPE_CATEGORICAL:
            assert polars_tables_helpers.__is_boolean(column, col_type) == False
            assert polars_tables_helpers.__is_numeric(column, col_type) == False


# 14
def test_vis_data_numeric_columns_simple():
    test_data = pl.DataFrame({"ints": list(range(10)) + list(range(10))})
    actual = polars_tables_helpers.get_value_occurrences_count(test_data)

    read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/polars/vis_data_integer_simple.txt'
    )


# 15
def test_vis_data_numeric_columns_with_bins():
    pass


# 16
def test_vis_data_bool_column():
    pass


# 17
def test_vis_data_categorical_column_percentage():
    pass


# 18
def test_vis_data_categorical_column_other():
    pass


# 19
def test_vis_data_categorical_column_unique():
    pass


# 20
def test_vis_data_categorical_column_switch_perc_to_unique():
    pass


# 21
def test_get_float_precision():
    assert __get_float_precision(None) == None
    assert __get_float_precision("%.2f") == 2
    assert __get_float_precision("%.12f") == 12
    assert __get_float_precision("%.12e") == None
    assert __get_float_precision("%d") == None
    assert __get_float_precision(1) == None



# def __prepare_describe_result(described_str):
#     """
#     This function is needed with the aim not to be depended on the python version,
#     there is different indentation in different python versions.
#     We check only the data, not the indentation.
#     """
#     # type: (str) -> (str)
#     result = []
#     for line in described_str.split("\n"):
#         result.append(" ".join(line.split()))
#
#     return "\n".join(result)

#
# @pytest.mark.skipif(sys.version_info < (3, 0),reason="")
# def test_vis_data_integer_columns_with_bins():
#     test_data = pd.DataFrame({"ints": list(range(21)) + list(range(21))})
#     actual = polars_tables_helpers.get_value_occurrences_count(test_data)
#     read_expected_from_file_and_compare_with_actual(
#         actual=actual,
#         expected_file='test_data/pandas/vis_data_integer_with_bins.txt'
#     )
#
#
# @pytest.mark.skipif(sys.version_info < (3, 0),reason="")
# def test_vis_data_float_columns_simple():
#     import numpy as np
#     test_data = pd.DataFrame({"floats": np.arange(0, 1, 0.1)})
#     actual = polars_tables_helpers.get_value_occurrences_count(test_data)
#     read_expected_from_file_and_compare_with_actual(
#         actual=actual,
#         expected_file='test_data/pandas/vis_data_float_simple.txt'
#     )
#
#
# @pytest.mark.skipif(sys.version_info < (3, 0),reason="")
# def test_vis_data_float_columns_with_bins():
#     import numpy as np
#     test_data = pd.DataFrame({"floats": np.arange(0, 3, 0.1)})
#     actual = polars_tables_helpers.get_value_occurrences_count(test_data)
#     read_expected_from_file_and_compare_with_actual(
#         actual=actual,
#         expected_file='test_data/pandas/vis_data_float_with_bins.txt'
#     )


def read_expected_from_file_and_compare_with_actual(actual, expected_file):
    with open(expected_file, 'r') as in_f:
        expected = in_f.read()

    # for a more convenient assertion fails messages here we compare string char by char
    for ind, (act, exp) in enumerate(zip(actual, expected)):
        assert act == exp, "index is %s, act part = %s, exp part = %s" % (ind,
            actual[max(0, ind - 20): min(len(actual) - 1, ind + 20)],
            expected[max(0, ind - 20): min(len(actual) - 1, ind + 20)])
