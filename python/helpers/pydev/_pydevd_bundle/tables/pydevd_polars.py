#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import polars as pl


def get_type(table):
    # type: (str) -> str
    return str(type(table))


def get_shape(table):
    # type: (pl.DataFrame) -> str
    return str(table.shape)


def get_head(table, max_cols):
    # type: (pl.DataFrame, int) -> str
    return table.head()._repr_html_()


def get_column_types(table):
    # type: (pl.DataFrame) -> str
    if type(table).__name__ == 'Series':
        return str(table.dtype)
    else:
        return ' '.join([str(t) for t in table.dtypes])


# used by pydevd, isDisplaySupported equals false
def get_data(table, max_cols, max_colwidth, start_index=None, end_index=None):
    # type: (pl.DataFrame, int, int, int, int) -> str
    return table[start_index:end_index]._repr_html_()


# used by DSTableCommands isDisplaySupported equals true
def display_data(table, max_cols, max_colwidth, start, end):
    # type: (pl.DataFrame, int, int, int, int) -> None
    print(table[start:end]._repr_html_())
