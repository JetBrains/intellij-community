#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""
Here we test aux methods for polars tables handling, namely,
check functions from _pydevd_bundle.tables.pydevd_polars module.
"""

import pytest

import polars as pl
import numpy as np

from datetime import datetime, date, time

import _pydevd_bundle.tables.pydevd_polars as polars_tables_helpers
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR
TYPE_BOOL, TYPE_NUMERIC, TYPE_CATEGORICAL = "bool", "numeric", "categorical"
pl_version_major, pl_version_minor, _ = pl.__version__.split(".")
polars_version = pl_version_major


@pytest.fixture
def setup_dataframe():
    """
    Here we create a fixture for tests that are related to DataFrames.
    We create a DataFrame with various data types.
    Also, we create other auxiliary data
    """
    if pl_version_major == "0":
        df = pl.DataFrame({
                "int_col": [1, 2, 3],
                "float_col": [1.0, 2.0, 3.0],
                "bool_col": [True, False, True],
                "str_col": ["one", "two", "three"],
                "date_col": [date(2022, 1, 1), date(2022, 1, 2), date(2022, 1, 3)],
                "datetime_col": [datetime(2022, 1, 1, 12, 0),
                                 datetime(2022, 1, 2, 12, 0),
                                 datetime(2022, 1, 3, 12, 0)],
                "categorical_col": ["A", "B", "A"],
        })
    else:
        df = pl.DataFrame({
                "int_col": [1, 2, 3],
                "float_col": [1.0, 2.0, 3.0],
                "bool_col": [True, False, True],
                "bool_col_with_none": [True, False, None],
                "str_col": ["one", "two", "three"],
                "date_col": [date(2022, 1, 1), date(2022, 1, 2), date(2022, 1, 3)],
                "datetime_col": [datetime(2022, 1, 1, 12, 0),
                                 datetime(2022, 1, 2, 12, 0),
                                 datetime(2022, 1, 3, 12, 0)],
                "time_col": [time(12, 0), time(12, 30), time(13, 0)],
                "categorical_col": ["A", "B", "A"],
                "binary_col": [b"abc", b"def", b"ghi"],
                "struct_col": [{"age": 30, "height": 5.5},
                               {"age": 25, "height": 6.1},
                               {"age": 35, "height": 5.9}],
                "list_col": [[1, 2], [3, 4], [5, 6]],
                "large_number": [1844674407370955, 1844674407370955, 1844674407370955]
        }, schema={"int_col": pl.Int64,
                   "float_col": pl.Float64,
                   "bool_col": pl.Boolean,
                   "bool_col_with_none": pl.Boolean,
                   "str_col": pl.Utf8,
                   "date_col": pl.Date,
                   "datetime_col": pl.Datetime,
                   "time_col": pl.Time,
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


@pytest.fixture
def setup_dataframe_with_float_values():
    if pl_version_major == "0":
        df = pl.DataFrame({
                "int_col": [1, 2, 3],
                "float_col": [1.0, 2.0, None],
        })
    else:
        df = pl.DataFrame({
                "int_col": [1, 2, 3],
                "float_col": [1.0, 2.0, None],
                "struct_col": [{"age": 30, "height": 5.5},
                               {"age": 25, "height": 6.1},
                               {"age": 35, "height": None}],
        })

    return df

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

    polars_tables_helpers.display_data_html(df, start_index=0, end_index=2)

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

    polars_tables_helpers.display_data_csv(df, start_index=0, end_index=2)

    after_config_state = pl.Config.state()

    assert len(before_config_state.keys()) == len(after_config_state.keys())
    for key in before_config_state.keys():
        assert before_config_state[key] == after_config_state[key], \
        f"For key {key} config state after getting data has been changed: before={before_config_state[key]}, after={after_config_state[key]}"


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

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/polars/major_version_' + polars_version +'/get_info_result.txt'
    )


# 7
def test_describe_many_numerical_columns_check_html(setup_dataframe_many_numeric_columns):
    df = setup_dataframe_many_numeric_columns
    actual = polars_tables_helpers.get_column_descriptions(df)

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/polars/major_version_' + polars_version + '/dataframe_many_numeric_columns_describe.txt'
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

    for column in df.columns:
        described_series = polars_tables_helpers.__get_describe(df[column])
        resulted += str(described_series.to_dict(as_series=False)) + "\n"

    __read_expected_from_file_and_compare_with_actual(
        actual=resulted,
        expected_file='test_data/polars/major_version_' + polars_version + '/series_describe.txt'
    )


# 13
def test_vis_data_detecting_column_type(setup_dataframe):
    _, df, _, _, col_name_to_data_type = setup_dataframe
    for column in df.columns:
        col_type = df[column].dtype
        if col_name_to_data_type[column] == TYPE_BOOL:
            assert polars_tables_helpers.__is_boolean(df[column], col_type) == True
            assert polars_tables_helpers.__is_numeric(df[column], col_type) == False
        elif col_name_to_data_type[column] == TYPE_NUMERIC:
            assert polars_tables_helpers.__is_boolean(df[column], col_type) == False
            assert polars_tables_helpers.__is_numeric(df[column], col_type) == True
        elif col_name_to_data_type[column] == TYPE_CATEGORICAL:
            assert polars_tables_helpers.__is_boolean(df[column], col_type) == False
            assert polars_tables_helpers.__is_numeric(df[column], col_type) == False


# 14
def test_vis_data_int_columns_simple():
    test_data = pl.DataFrame({"ints": list(range(10))})
    actual = polars_tables_helpers.get_value_occurrences_count(test_data)

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/polars/vis_data_int_column_simple.txt'
    )


# 15
def test_vis_data_int_columns_with_bins():
    test_data = pl.DataFrame({"ints": list(range(50))})
    actual = polars_tables_helpers.get_value_occurrences_count(test_data)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/vis_data_int_column_with_bins.txt'
    )


# 16
def test_vis_data_float_columns_simple():
    test_data_floats = pl.DataFrame({"floats": [1.1, 2.2, 3.3, 4.4, 5.5]})
    actual = polars_tables_helpers.get_value_occurrences_count(test_data_floats)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/vis_data_float_column_simple.txt'
    )


# 17
def test_vis_data_float_columns_with_bins():
    test_data_floats = pl.DataFrame({"floats": [1.1, 2.2, 3.3, 4.4, 5.5, 6.6, 7.7, 8.8, 9.9, 10.1, 11.1, 12.2] * 100})
    actual = polars_tables_helpers.get_value_occurrences_count(test_data_floats)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/vis_data_float_column_with_bins.txt'
    )


# 18
def test_vis_data_bool_column():
    test_data_bool = pl.DataFrame({"bools": [True] * 50 + [False] * 25})
    actual = polars_tables_helpers.get_value_occurrences_count(test_data_bool)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/vis_data_bool_column.txt'
    )


# 19
def test_vis_data_categorical_column_percentage():
    test_data_str = pl.DataFrame({"strs": ["First"] * 50 + ["Second"] * 25})
    actual = polars_tables_helpers.get_value_occurrences_count(test_data_str)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/vis_data_categorical_column.txt'
    )


# 20
def test_vis_data_categorical_column_other():
    test_data_str_other = pl.DataFrame({"strs": ["First"] * 50 + ["Second"] * 25 + ["Third"] * 10 + ["Forth"] * 5})
    actual = polars_tables_helpers.get_value_occurrences_count(test_data_str_other)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/vis_data_categorical_column_other.txt'
    )


# 21
def test_vis_data_categorical_column_unique():
    test_data_str_unique = pl.DataFrame({"strs": [str(i) for i in range(1000)]})
    actual = polars_tables_helpers.get_value_occurrences_count(test_data_str_unique)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/vis_data_categorical_column_unique.txt'
    )


# 22
def test_vis_data_categorical_column_switch_perc_to_unique():
    # we need a column with 49% of unique values
    test_data_other = pl.DataFrame({"ints": [str(i) for i in range(49)] + ["48"] * 51})
    assert polars_tables_helpers.ColumnVisualisationType.PERCENTAGE in polars_tables_helpers.get_value_occurrences_count(test_data_other)

    # if the share of unique is greater than 50% then we should show "UNIQUE" vis
    test_data_unique = pl.DataFrame({"ints": [str(i) for i in range(52)] + ["51"] * 49})
    assert polars_tables_helpers.ColumnVisualisationType.UNIQUE in polars_tables_helpers.get_value_occurrences_count(test_data_unique)


# 23
def test_get_float_precision():
    assert polars_tables_helpers.__get_float_precision(None) is None
    assert polars_tables_helpers.__get_float_precision("%.2f") == 2
    assert polars_tables_helpers.__get_float_precision("%.12f") == 12
    assert polars_tables_helpers.__get_float_precision("%.12e") is None
    assert polars_tables_helpers.__get_float_precision("%d") is None
    assert polars_tables_helpers.__get_float_precision(1) is None


# 24
def test_get_data_numeric_dataframe(setup_dataframe_many_numeric_columns):
    df = setup_dataframe_many_numeric_columns
    actual = polars_tables_helpers.get_data(df, False, 0, 2)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/major_version_' + polars_version + '/get_data_numeric_dataframe.txt'
    )


# 25
def test_get_data_numeric_dataframe_second_page(setup_dataframe_many_numeric_columns):
    df = setup_dataframe_many_numeric_columns
    actual = polars_tables_helpers.get_data(df, False, 2, 4)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/major_version_' + polars_version + '/get_data_numeric_dataframe_second_page.txt'
    )


# 26
def test_get_data_empty_series(setup_series_empty):
    s = setup_series_empty
    actual = polars_tables_helpers.get_data(s, False, 0, 2)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/major_version_' + polars_version + '/get_data_empty_series.txt'
    )


# 27
def test_get_data_empty_series_second_page(setup_series_empty):
    s = setup_series_empty
    actual = polars_tables_helpers.get_data(s, False, 2, 4)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/major_version_' + polars_version + '/get_data_empty_series_second_page.txt'
    )


# 28
def test_get_data_unnamed_series(setup_series_empty):
    s = setup_series_empty
    actual = polars_tables_helpers.get_data(s, False, 0, 2)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/major_version_' + polars_version + '/get_data_unnamed_series.txt'
    )


# 29
def test_get_data_unnamed_series_second_page(setup_series_empty):
    s = setup_series_empty
    actual = polars_tables_helpers.get_data(s, False, 2, 4)

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/major_version_' + polars_version + '/get_data_unnamed_series_second_page.txt'
    )

# 30
def test_get_data_float_values_2f(setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values
    actual = polars_tables_helpers.get_data(df, False, 0, 3, format="%.2f")

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/major_version_' + polars_version + '/get_data_float_values_2f.txt'
    )


# 31
def test_get_data_float_values_2fgarbage(setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values
    actual = polars_tables_helpers.get_data(df, False, 0, 3, format="%.2fgarbage")

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/major_version_' + polars_version + '/get_data_float_values_2fgarbage.txt'
    )


# 32
def test_get_data_float_values_0f(setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values
    actual = polars_tables_helpers.get_data(df, False, 0, 3, format="%.0f")

    __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/polars/major_version_' + polars_version + '/get_data_float_values_0f.txt'
    )

# 33
def test_display_data_html_df(capsys, setup_dataframe):
    _, df, _, _, _ = setup_dataframe

    polars_tables_helpers.display_data_html(df, 0, 3)

    # Capture the output
    captured = capsys.readouterr()

    __read_expected_from_file_and_compare_with_actual(
        actual=captured.out,
        expected_file='test_data/polars/major_version_' + polars_version + '/display_data_html_df.txt'
    )


# 34
def test_display_data_html_df_with_float_values(capsys, setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values

    polars_tables_helpers.display_data_html(df, 0, 3)

    # Capture the output
    captured = capsys.readouterr()

    __read_expected_from_file_and_compare_with_actual(
        actual=captured.out,
        expected_file='test_data/polars/major_version_' + polars_version + '/display_data_html_df_with_float_values.txt'
    )


def __read_expected_from_file_and_compare_with_actual(actual, expected_file):
    with open(expected_file, 'r') as in_f:
        expected = in_f.read()
    assert len(expected) > 0, "The expected file is empty"

    # for a more convenient assertion fails messages here we compare string char by char
    for ind, (act, exp) in enumerate(zip(actual, expected)):
        assert act == exp, "\nindex is %s, \n\nact part = %s \n\nexp part = %s\n" % (ind, actual, expected)
