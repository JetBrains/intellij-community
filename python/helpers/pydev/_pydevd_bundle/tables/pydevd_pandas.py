#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import pandas as pd

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'


def get_type(table):
    # type: (str) -> str
    return str(type(table))


# noinspection PyUnresolvedReferences
def get_shape(table):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray]) -> str
    return str(table.shape[0])


# noinspection PyUnresolvedReferences
def get_head(table, max_cols):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray], int) -> str
    max_cols = __check_max_cols(max_cols)
    return repr(__convert_to_df(table).head().to_html(notebook=True, max_cols=max_cols))


# noinspection PyUnresolvedReferences
def get_column_types(table):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray]) -> str
    table = __convert_to_df(table)
    return str(table.index.dtype) + TABLE_TYPE_NEXT_VALUE_SEPARATOR + \
        TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(t) for t in table.dtypes])


# used by pydevd
# noinspection PyUnresolvedReferences
def get_data(table, max_cols, max_colwidth, start_index=None, end_index=None):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray], int, int, int, int) -> str
    max_cols = __check_max_cols(max_cols)
    _jb_max_cols = pd.get_option('display.max_columns')
    _jb_max_colwidth = pd.get_option('display.max_colwidth')

    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_colwidth', max_colwidth)

    if start_index is not None and end_index is not None:
        table = __get_data_slice(table, start_index, end_index)

    data = repr(__convert_to_df(table).to_html(notebook=True, max_cols=max_cols))

    pd.set_option('display.max_columns', _jb_max_cols)
    pd.set_option('display.max_colwidth', _jb_max_colwidth)

    return data


def __get_data_slice(table, start, end):
    return __convert_to_df(table).iloc[start:end]


# used by DSTableCommands
# noinspection PyUnresolvedReferences
def display_data(table, max_cols, max_colwidth, start, end):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray], int, int, int, int) -> None
    from IPython.display import display
    max_cols = __check_max_cols(max_cols)

    _jb_max_cols = pd.get_option('display.max_columns')
    _jb_max_colwidth = pd.get_option('display.max_colwidth')

    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_colwidth', max_colwidth)

    display(__convert_to_df(table).iloc[start:end])

    pd.set_option('display.max_columns', _jb_max_cols)
    pd.set_option('display.max_colwidth', _jb_max_colwidth)


def get_column_descriptions(table, max_cols, max_colwidth):
    # type: (pd.DataFrame, int, int) -> str
    described_df = __get_describe_df(table)

    return get_data(described_df, max_cols, max_colwidth, None, None)


def get_value_counts(table, max_cols, max_colwidth):
    # type: (pd.DataFrame, int, int) -> str
    count_df = __get_counts_df(table)

    return get_data(count_df, max_cols, max_colwidth, None, None)


def __get_describe_df(table):
    # type: (pd.DataFrame) -> pd.DataFrame
    described_df = table.describe(percentiles=[.05, .25, .5, .75, .95],
                                  exclude=[np.complex64, np.complex128])
    return described_df.reindex(columns=table.columns, copy=False)


def __get_counts_df(table):
    # type: (pd.DataFrame) -> pd.DataFrame
    return table.count().to_frame().transpose()


# noinspection PyUnresolvedReferences
def __convert_to_df(table):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray, pd.Categorical]) -> pd.DataFrame
    if type(table) is pd.Series:
        return __series_to_df(table)
    if type(table) is np.ndarray:
        return __array_to_df(table)
    if type(table) is pd.Categorical:
        return __categorical_to_df(table)
    return table


# pandas.Series support
def __get_column_name(table):
    # type: (pd.Series) -> str
    if table.name is not None:
        # noinspection PyTypeChecker
        return table.name
    return '<unnamed>'


def __series_to_df(table):
    # type: (pd.Series) -> pd.DataFrame
    return table.to_frame(name=__get_column_name(table))


# numpy.array support
# TODO: extract to a dedicated provider to fix DS-2086
def __array_to_df(table):
    # type: (np.ndarray) -> pd.DataFrame
    return pd.DataFrame(table)


def __categorical_to_df(table):
    # type: (pd.Categorical) -> pd.DataFrame
    return pd.DataFrame(table)


def __check_max_cols(max_cols):
    # type ([int, None] -> [int, None])
    if max_cols is None or max_cols < 0:
        return None
    return max_cols
