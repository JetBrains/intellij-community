#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""
Here we test whether the commands generated for sorting pandas objects in Python work correctly.
The tests for generating the commands are in DSSortingTest.kt
"""

import pandas as pd
import pytest
from _pydevd_bundle.pydevd_utils import eval_expression

@pytest.fixture
def unsorted_table():
    df = pd.DataFrame([[1, 'A'], [1, 'B'],
                       [2, 'D'], [2, 'C'],
                       [1, 'D'], [1, 'C'],
                       [2, 'A'], [2, 'B']], columns=['column 0', 'column 1'])
    return df

@pytest.fixture
def sorted_table():
    df = pd.DataFrame([[1, 'A'], [1, 'B'],
                       [1, 'C'], [1, 'D'],
                       [2, 'A'], [2, 'B'],
                       [2, 'C'], [2, 'D']], index=[0, 1, 5, 4, 6, 7, 3, 2],
                      columns=['column 0', 'column 1'])
    return df


def test_sorting_series():
    df = pd.Series([1, 3, 2])

    sorting_command = "df.sort_values(ascending=[True])"
    actual_series = eval_expression(sorting_command, {}, {"df": df})

    expected_series = pd.Series([1, 2, 3], index=[0, 2, 1])

    assert actual_series.equals(expected_series)


def test_sorting_df_by_several_columns(unsorted_table, sorted_table):
    df = unsorted_table

    sorting_command = "df.sort_values(by=[df.columns[0], df.columns[1]], ascending=[True, True])"
    actual_df = eval_expression(sorting_command, {}, {"df": df})

    expected_df = sorted_table

    assert actual_df.equals(expected_df)


def test_sorting_df_by_several_indexes(unsorted_table, sorted_table):
    df = unsorted_table
    df.set_index(['column 0', 'column 1'], inplace=True)

    expected_df = sorted_table
    expected_df.set_index(['column 0', 'column 1'], inplace=True)

    sorting_command = "df.sort_index(level=[0,1], ascending=[True,True])"
    actual_df = eval_expression(sorting_command, {}, {"df": df})

    assert actual_df.equals(expected_df)


def test_sorting_indexes_range_type():
    df = pd.DataFrame({'A': [1, 2, 3, 4]},
                      index=pd.RangeIndex(start=0, stop=4, step=1))
    expected_df = pd.DataFrame({'A': [4, 3, 2, 1]},
                             index=pd.RangeIndex(start=3, stop=-1, step=-1))

    sorting_command = "df.sort_index(ascending=False)"
    actual_df = eval_expression(sorting_command, {}, {"df": df})

    assert actual_df.equals(expected_df)


def test_sorting_multi_index_with_range_index():
    arrays = [pd.RangeIndex(start=0, stop=3, step=1), ['A', 'B', 'B']]
    multi_index = pd.MultiIndex.from_arrays(arrays, names=('Range', 'Letter'))
    df = pd.DataFrame({'Values': [10, 20, 30]}, index=multi_index)

    sorted_indexes = [[1, 2, 0], ['B', 'B', 'A']]
    expected_df = pd.DataFrame({'Values': [20, 30, 10]}, index=sorted_indexes)

    sorting_command = "df.sort_index(level=[1,0], ascending=[False, True])"
    actual_df = eval_expression(sorting_command, {}, {"df": df})

    assert actual_df.equals(expected_df)
