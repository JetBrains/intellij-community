#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import io
import sys
import pandas as pd


def get_type(table) -> str:
    return str(type(table))


def get_shape(table) -> str:
    return str(table.shape[0])


def get_head(table, max_cols) -> str:
    return repr(table.head().to_html(notebook=True, max_cols=max_cols))


def get_column_types(table) -> str:
    with io.StringIO() as output:
        print(table.index.dtype, *[str(t) for t in table.dtypes],
              file=output)
        return output.getvalue()


# TODO: check if we actually need all three
# used by pydevd
def get_data(table, max_cols):
    _jb_max_cols = pd.get_option('display.max_columns')
    _jb_max_colwidth = pd.get_option('display.max_colwidth')

    pd.set_option('display.max_colwidth', max_cols)
    data = repr(table.to_html(notebook=True, max_cols=max_cols))
    pd.set_option('display.max_colwidth', _jb_max_colwidth)

    return data


# used by DSTableCommands
def get_data_slice(table, start, end):
    return table.iloc[start:end]


# used by DSTableCommands
def display_data(table, max_cols, max_colwidth, start, end):
    from IPython.display import display

    _jb_max_cols = pd.get_option('display.max_columns')
    _jb_max_colwidth = pd.get_option('display.max_colwidth')

    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_colwidth', max_colwidth)

    display(table.iloc[start:end])

    pd.set_option('display.max_columns', _jb_max_cols)
    pd.set_option('display.max_colwidth', _jb_max_colwidth)
