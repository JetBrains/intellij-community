#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""
Here we test aux methods for pandas tables handling, namely,
check functions from _pydevd_bundle.tables.pydevd_pandas module.
"""

import pandas as pd
import pytest
import sys

from IPython.display import HTML

import _pydevd_bundle.tables.pydevd_pandas as pandas_tables_helpers
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR
TYPE_BOOL, TYPE_NUMERIC, TYPE_CATEGORICAL = "bool", "numeric", "categorical"
test_data_dir = 'python_' + str(sys.version_info[0]) + '_' + str(sys.version_info[1])


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
            "bool_with_nan": [True, False, False, None],
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
            "lists": [[1, 2], [1, 2], [3, 4], [4, 5]],
            "dicts": [{1: 2}, {1: 2}, {3: 4}, {4, 5}],
            "tuples": [(1, 2), (1, 2), (3, 4), (4, 5)],
        }
    )
    df['datetime64[ns]'] = df['datetime64[ns]'].astype("datetime64[ns]")
    df['I'] = df['I'].astype("datetime64[ns]")
    df_html = repr(df.head(1).to_html(notebook=True, max_cols=None))
    columns_types = [str(df.index.dtype)] + [str(t) for t in df.dtypes]

    col_name_to_data_type = {
        "A": TYPE_NUMERIC,
        "B": TYPE_CATEGORICAL,
        "C": [None] * rows_number,
        "D": TYPE_CATEGORICAL,
        "E": TYPE_CATEGORICAL,
        "F": TYPE_CATEGORICAL,
        "G": TYPE_CATEGORICAL,
        "H": TYPE_BOOL,
        "bool_with_nan": TYPE_CATEGORICAL,
        "I": TYPE_CATEGORICAL,
        "J": TYPE_NUMERIC,
        "K": TYPE_NUMERIC,
        "L": TYPE_CATEGORICAL,
        "dates": TYPE_CATEGORICAL,
        "datetime64[ns]": TYPE_CATEGORICAL,
        "datetime64[ns, <tz>]": TYPE_CATEGORICAL,
        "period": TYPE_CATEGORICAL,
        "category": TYPE_CATEGORICAL,
        "interval": TYPE_CATEGORICAL,
        "lists": TYPE_CATEGORICAL,
        "dicts": TYPE_CATEGORICAL,
        "tuples": TYPE_CATEGORICAL,
    }

    return rows_number, df, df_html, columns_types, col_name_to_data_type


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


@pytest.fixture
def setup_dataframe_with_float_values():
    if test_data_dir.startswith("python_2"):
        df = pd.DataFrame({
            "int_col": [1, 2, 3],
            "float_col": [1.0, 2.0, None],
            "strings": ["f", "s", None],
            "list": [[1.1, 2.2], [2.2, 3.3], [4.4, None]],
            "complex": [1.0 + 2j, 2.2 + 3j, 4.4 + 5j]
        })
    else:
        df = pd.DataFrame({
            "int_col": [1, 2, 3],
            "float_col": [1.0, 2.0, None],
            "strings": ["f", "s", None],
            "dict": [{"age": 30, "height": 5.5},
                     {"age": 25, "height": 6.1},
                     {"age": 35, "height": None}],
            "list": [[1.1, 2.2], [2.2, 3.3], [4.4, None]],
            "complex": [1.0 + 2j, 2.2 + 3j, 4.4 + 5j]
        })

    return df


# 1
def test_info_command(setup_dataframe):
    """
    Here we check the correctness of info command that is invoked via Kotlin.
    :param setup_dataframe: fixture/data for the test
    """
    rows, df, df_html, cols_types_expected, _ = setup_dataframe

    cols_types_actual = pandas_tables_helpers.get_column_types(df)
    cols_types_actual = cols_types_actual.split(pandas_tables_helpers.TABLE_TYPE_NEXT_VALUE_SEPARATOR)

    assert pandas_tables_helpers.get_type(df) == str(pd.DataFrame)
    assert pandas_tables_helpers.get_shape(df) == str(rows)
    assert pandas_tables_helpers.get_head(df) == df_html
    assert cols_types_actual == cols_types_expected


# 2
def test_get_data_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _, _ = setup_dataframe

    max_columns_before = pd.get_option('display.max_columns')
    max_colwidth_before = pd.get_option('display.max_colwidth')
    max_rows_before = pd.get_option('display.max_rows')

    pandas_tables_helpers.get_data(df, False, format="%.2f")

    max_columns_after = pd.get_option('display.max_columns')
    max_colwidth_after = pd.get_option('display.max_colwidth')
    max_rows_after = pd.get_option('display.max_rows')

    assert max_columns_before == max_columns_after
    assert max_colwidth_before == max_colwidth_after
    assert max_rows_before == max_rows_after


# 3
def test_display_html_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _, _ = setup_dataframe

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


# 4
def test_display_csv_saves_display_options(setup_dataframe):
    """
    We check that we don't ruin a user's display options.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _, _ = setup_dataframe

    max_columns_before = pd.get_option('display.max_columns')
    max_colwidth_before = pd.get_option('display.max_colwidth')
    max_rows_before = pd.get_option('display.max_rows')

    pandas_tables_helpers.display_data_csv(df, start_index=0, end_index=2)

    max_columns_after = pd.get_option('display.max_columns')
    max_colwidth_after = pd.get_option('display.max_colwidth')
    max_rows_after = pd.get_option('display.max_rows')

    assert max_columns_before == max_columns_after
    assert max_colwidth_before == max_colwidth_after
    assert max_rows_before == max_rows_after


# 5
def test_convert_to_df_unnamed_series(setup_series_no_names):
    """
    In this test we check two methods: __convert_to_df and __get_column_name.
    For unnamed pd.Series case.
    :param setup_series_no_names: fixture/data for the test
    """
    converted_series = pandas_tables_helpers.__convert_to_df(setup_series_no_names)

    assert isinstance(converted_series, pd.DataFrame)
    assert converted_series.columns[0] == '<unnamed>'


# 6
def test_convert_to_df_common_series(setup_dataframe):
    """
    In this test we check two methods: __convert_to_df and __get_column_name.
    For a common pd.Series case.
    :param setup_dataframe: fixture/data for the test
    """
    _, df, _, _, _ = setup_dataframe
    for col in df.columns:
        converted_series = pandas_tables_helpers.__convert_to_df(df[col])

        assert isinstance(converted_series, pd.DataFrame)
        assert converted_series.columns[0] == col


# 7
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
    _, df, _, _, _ = setup_dataframe

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

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/getInfo_result.txt'
    )


# 8
@pytest.mark.skipif(sys.version_info < (3, 0), reason="Different format for Python2")
def test_describe_many_columns_check_html(setup_dataframe_many_columns):
    df = setup_dataframe_many_columns
    actual = pandas_tables_helpers.get_column_descriptions(df)

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/dataframe_many_columns_describe_after.txt'
    )


# 9
def test_describe_shape_numeric_types(setup_dataframe_many_columns):
    df = setup_dataframe_many_columns
    describe_df = pandas_tables_helpers.__get_describe(df)

    # for dataframes with only numeric types in columns we have 10 statistics
    assert describe_df.shape[0] == 10
    # the number of columns should be the same
    assert describe_df.shape[1] == df.shape[1]


# 10
def test_describe_shape_all_types(setup_dataframe):
    _, df, _, _, _ = setup_dataframe

    if sys.version_info < (3, 0):
        df = df.drop(columns=['lists', 'dicts', 'tuples'])

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


# 11
def test_get_describe_save_columns(setup_dataframe):
    _, df, _, _, _ = setup_dataframe

    if sys.version_info < (3, 0):
        df = df.drop(columns=['lists', 'dicts', 'tuples'])

    describe_df = pandas_tables_helpers.__get_describe(df)
    original_columns, describe_columns = df.columns.tolist(), describe_df.columns.tolist()

    # the number of columns is the same in described and in original
    assert len(original_columns) == len(describe_columns)

    # compare columns and it's order
    for expected, actual in zip(original_columns, describe_columns):
        assert expected == actual


# 12
def test_get_describe_returned_types(setup_dataframe):
    _, df, _, _, _ = setup_dataframe

    if sys.version_info < (3, 0):
        df = df.drop(columns=['lists', 'dicts', 'tuples'])

    assert type(pandas_tables_helpers.__get_describe(df)) == pd.DataFrame
    assert type(pandas_tables_helpers.__get_describe(df['A'])) == pd.Series


# 13
@pytest.mark.skipif(sys.version_info < (3, 0), reason="Different format for Python2")
def test_describe_series(setup_dataframe):
    _, df, _, _, _ = setup_dataframe

    resulted = ""

    for column in df:
        # we skip dates column because its data every time is different
        if column != 'dates' and column != 'interval':
            described_series = pandas_tables_helpers.__get_describe(df[column])
            if described_series is not None:
                resulted += str(described_series.to_dict()) + "\n"
            else:
                resulted += "\n"

    __read_expected_from_file_and_compare_with_actual(
        actual=resulted,
        expected_file='test_data/pandas/' + test_data_dir + '/series_describe.txt'
    )


# 14
@pytest.mark.skipif(sys.version_info < (3, 0),
                    reason="The exception will be raised during df creation in Python2")
def test_overflow_error_is_caught(setup_df_with_big_int_values):
    df = setup_df_with_big_int_values
    assert pandas_tables_helpers.__get_describe(df) is None


# 15
def test_vis_data_detecting_column_type(setup_dataframe):
    _, df, _, _, col_name_to_data_type = setup_dataframe
    for column in df.columns:
        col_type = df[column].dtype
        if col_name_to_data_type[column] == TYPE_BOOL:
            assert pandas_tables_helpers.__is_boolean(col_type) == True, "column is %s, col_type is %s, col_type_kind is %s"   % (column, col_type, col_type.kind)
            assert pandas_tables_helpers.__is_categorical(df[column], col_type) == False, "column is %s, col_type is %s, col_type_kind is %s"   % (column, col_type, col_type.kind)
            assert pandas_tables_helpers.__is_numeric(col_type) == False, "column is %s, col_type is %s, col_type_kind is %s"   % (column, col_type, col_type.kind)
        elif col_name_to_data_type[column] == TYPE_NUMERIC:
            assert pandas_tables_helpers.__is_boolean(col_type) == False, "column is %s, col_type is %s, col_type_kind is %s"   % (column, col_type, col_type.kind)
            assert pandas_tables_helpers.__is_categorical(df[column], col_type) == False, "column is %s, col_type is %s, col_type_kind is %s"   % (column, col_type, col_type.kind)
            assert pandas_tables_helpers.__is_numeric(col_type) == True, "column is %s, col_type is %s, col_type_kind is %s"   % (column, col_type, col_type.kind)
        elif col_name_to_data_type[column] == TYPE_CATEGORICAL:
            assert pandas_tables_helpers.__is_boolean(col_type) == False, "column is %s, col_type is %s, col_type_kind is %s"   % (column, col_type, col_type.kind)
            assert pandas_tables_helpers.__is_categorical(df[column], col_type) == True, "column is %s, col_type is %s, col_type_kind is %s"   % (column, col_type, col_type.kind)
            assert pandas_tables_helpers.__is_numeric(col_type) == False, "column is %s, col_type is %s, col_type_kind is %s"   % (column, col_type, col_type.kind)


# 16
def test_vis_data_integer_columns_simple():
    test_data = pd.DataFrame({"ints": list(range(10)) + list(range(10))})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data)
    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_integer_simple.txt'
    )


# 17
@pytest.mark.skipif(sys.version_info < (3, 0),reason="")
def test_vis_data_integer_columns_with_bins():
    test_data = pd.DataFrame({"ints": list(range(21)) + list(range(21))})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data)
    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_integer_with_bins.txt'
    )


# 18
@pytest.mark.skipif(sys.version_info < (3, 0),reason="")
def test_vis_data_float_columns_simple():
    import numpy as np
    test_data = pd.DataFrame({"floats": np.arange(0, 1, 0.1)})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data)
    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_float_simple.txt'
    )


# 19
@pytest.mark.skipif(sys.version_info < (3, 0),reason="")
def test_vis_data_float_columns_with_bins():
    import numpy as np
    test_data = pd.DataFrame({"floats": np.arange(0, 3, 0.1)})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data)
    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_float_with_bins.txt'
    )


# 20
def test_vis_data_bool_column():
    test_data_bool = pd.DataFrame({"bools": [True] * 50 + [False] * 25})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data_bool)

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_bool_column.txt'
    )


# 21
def test_vis_data_bool_with_nan_column():
    test_data_bool = pd.DataFrame({"bools": [True] * 50 + [False] * 25 + [None] * 10})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data_bool)
    if test_data_dir.startswith('python_2'):
        __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/pandas/python_2_7/vis_data_bool_with_nan_column.txt'
        )
    else:
        __read_expected_from_file_and_compare_with_actual(
            actual=actual,
            expected_file='test_data/pandas/vis_data_bool_with_nan_column.txt'
        )



# 22
def test_vis_data_categorical_column_percentage():
    test_data_str = pd.DataFrame({"strs": ["First"] * 50 + ["Second"] * 25})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data_str)

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_categorical_column.txt'
    )


# 23
def test_vis_data_categorical_column_other():
    test_data_str_other = pd.DataFrame({"strs": ["First"] * 50 + ["Second"] * 25 + ["Third"] * 10 + ["Forth"] * 5})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data_str_other)

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_categorical_column_other.txt'
    )


# 24
def test_vis_data_categorical_column_unique():
    test_data_str_unique = pd.DataFrame({"strs": [str(i) for i in range(1000)]})
    actual = pandas_tables_helpers.get_value_occurrences_count(test_data_str_unique)

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/vis_data_categorical_column_unique.txt'
    )


# 25
def test_vis_data_categorical_column_switch_perc_to_unique():
    # we need a column with 49% of unique values
    test_data_other = pd.DataFrame({"str": [str(i) for i in range(49)] + ["48"] * 51})
    assert pandas_tables_helpers.ColumnVisualisationType.PERCENTAGE in pandas_tables_helpers.get_value_occurrences_count(test_data_other)

    # if the share of unique is greater than 50% then we should show "UNIQUE" vis
    test_data_unique = pd.DataFrame({"str": [str(i) for i in range(52)] + ["51"] * 49})
    assert pandas_tables_helpers.ColumnVisualisationType.UNIQUE in pandas_tables_helpers.get_value_occurrences_count(test_data_unique)


# 26
def test_define_format_function():
    assert pandas_tables_helpers.__define_format_function(None) is None
    assert pandas_tables_helpers.__define_format_function('null') is None
    assert pandas_tables_helpers.__define_format_function('garbage') is None
    assert pandas_tables_helpers.__define_format_function(1) is None

    format_to_result = {
        "%.2f": (1.1, "1.10"),
        "%.12f": (1.1, "1.100000000000"),
        "%.2e": (1.1, "1.10e+00"),
        "%d": (1.1, "1"),
        "%d garbage": (1.1, "1 garbage"),
    }
    for format_str, (float_value, expected_result) in format_to_result.items():
        formatter = pandas_tables_helpers.__define_format_function(format_str)
        assert formatter is not None
        assert callable(formatter)
        assert formatter(float_value) == expected_result


# 27
def test_get_tables_display_options():
    max_cols, max_colwidth, max_rows = pandas_tables_helpers.__get_tables_display_options()
    assert max_cols is None
    assert max_rows is None
    if sys.version_info < (3, 0) or int(pd.__version__.split('.')[0]) < 1:
        assert max_colwidth == pandas_tables_helpers.MAX_COLWIDTH
    else:
        assert max_colwidth is None


# 28
def test_get_data_float_values_2f(setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values
    actual = pandas_tables_helpers.get_data(df, False, 0, 3, format="%.2f")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/' + test_data_dir + '/get_data_float_values_2f.txt'
    )


# 29
def test_get_data_float_values_12f(setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values
    actual = pandas_tables_helpers.get_data(df, False, 0, 3, format="%.12f")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/' + test_data_dir + '/get_data_float_values_12f.txt'
    )


# 30
def test_get_data_float_values_2e(setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values
    actual = pandas_tables_helpers.get_data(df, False, 0, 3, format="%.2e")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/' + test_data_dir + '/get_data_float_values_2e.txt'
    )


# 31
@pytest.mark.skipif(sys.version_info < (3, 0), reason="%d doesn't work with np.float('nan')")
def test_get_data_float_values_d(setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values
    actual = pandas_tables_helpers.get_data(df, False, 0, 3, format="%d")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/' + test_data_dir + '/get_data_float_values_d.txt'
    )


# 32
@pytest.mark.skipif(sys.version_info < (3, 0), reason="%d doesn't work with np.float('nan')")
def test_get_data_float_values_d_garbage(setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values
    actual = pandas_tables_helpers.get_data(df, False, 0, 3, format="%d garbage")

    __read_expected_from_file_and_compare_with_actual(
        actual=actual,
        expected_file='test_data/pandas/' + test_data_dir + '/get_data_float_values_d_garbage.txt'
    )


# 33
def test_display_data_html_df(mocker, setup_dataframe):
    _, df, _, _, _ = setup_dataframe
    df = df.drop(columns=['dates'])
    # Mock the HTML and display functions
    mock_display = mocker.patch('IPython.display.display')

    pandas_tables_helpers.display_data_html(df, 0, 16)

    called_args, called_kwargs = mock_display.call_args
    displayed_html = called_args[0]

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_html.data,
        expected_file='test_data/pandas/' + test_data_dir + '/display_data_html_df.txt'
    )


# 34
def test_display_data_html_df_with_float_values(mocker, setup_dataframe_with_float_values):
    df = setup_dataframe_with_float_values

    # Mock the HTML and display functions
    mock_display = mocker.patch('IPython.display.display')

    pandas_tables_helpers.display_data_html(df, 0, 3)

    called_args, called_kwargs = mock_display.call_args
    displayed_html = called_args[0]

    __read_expected_from_file_and_compare_with_actual(
        actual=displayed_html.data,
        expected_file='test_data/pandas/' + test_data_dir + '/display_data_html_df_with_float_values.txt'
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


def __read_expected_from_file_and_compare_with_actual(actual, expected_file):
    with open(expected_file, 'r') as in_f:
        expected = in_f.read()
    assert len(expected) > 0, "The expected file is empty"

    # for a more convenient assertion fails messages here we compare string char by char
    for ind, (act, exp) in enumerate(zip(actual, expected)):
        assert act == exp, "\nindex is %s \n\n act part = %s \n\n exp part = %s\n" % (ind, actual, expected)
