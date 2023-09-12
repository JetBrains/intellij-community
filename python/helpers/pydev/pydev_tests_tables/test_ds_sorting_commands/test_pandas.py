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


def test_commands(unsorted_table, sorted_table):
    df = pd.Series([1, 3, 2])
    sorting_command = "df.sort_values(ascending=[True])"
    series = eval_expression(sorting_command, {}, {"df": df})
    new_series = pd.Series([1, 2, 3], index=[0, 2, 1])

    assert series.equals(new_series)

    df = unsorted_table
    sorting_command = "df.sort_values(by=[df.columns[0], df.columns[1]], ascending=[True, True])"
    table = eval_expression(sorting_command, {}, {"df": df})
    new_table = sorted_table

    assert table.equals(new_table)

    df.set_index(['column 0', 'column 1'], inplace=True)
    new_table.set_index(['column 0', 'column 1'], inplace=True)
    sorting_command = "df.sort_index(level=[0,1], ascending=[True,True])"
    table = eval_expression(sorting_command, {}, {"df": df})

    assert table.equals(new_table)
