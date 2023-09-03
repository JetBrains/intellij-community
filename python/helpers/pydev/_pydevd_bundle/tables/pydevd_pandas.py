#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import pandas as pd

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH_PYTHON_2 = 100000


def get_type(table):
    # type: (str) -> str
    return str(type(table))


# noinspection PyUnresolvedReferences
def get_shape(table):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray]) -> str
    return str(table.shape[0])


# noinspection PyUnresolvedReferences
def get_head(table):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray]) -> str
    return repr(__convert_to_df(table).head().to_html(notebook=True, max_cols=None))


# noinspection PyUnresolvedReferences
def get_column_types(table):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray]) -> str
    table = __convert_to_df(table)
    return str(table.index.dtype) + TABLE_TYPE_NEXT_VALUE_SEPARATOR + \
        TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(t) for t in table.dtypes])


# used by pydevd
# noinspection PyUnresolvedReferences
def get_data(table, start_index=None, end_index=None):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray], int, int) -> str
    max_cols, max_colwidth = __get_tables_display_options()

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
def display_data(table, start, end):
    # type: (Union[pd.DataFrame, pd.Series, np.ndarray], int, int) -> None
    from IPython.display import display
    max_cols, max_colwidth = __get_tables_display_options()

    _jb_max_cols = pd.get_option('display.max_columns')
    _jb_max_colwidth = pd.get_option('display.max_colwidth')

    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_colwidth', max_colwidth)

    display(__convert_to_df(table).iloc[start:end])

    pd.set_option('display.max_columns', _jb_max_cols)
    pd.set_option('display.max_colwidth', _jb_max_colwidth)


def get_column_descriptions(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> str
    described_result = __get_describe(table)

    if described_result is not None:
        return get_data(described_result, None, None)
    else:
        return ""


def get_value_counts(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> str
    counts_result = __get_counts(table)

    return get_data(counts_result, None, None)


def __get_describe(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> Union[pd.DataFrame, pd.Series, None]
    try:
        described_ = table.describe(percentiles=[.05, .25, .5, .75, .95],
                                    exclude=[np.complex64, np.complex128])
    except (TypeError, OverflowError):
        return

    if type(table) is pd.Series:
        return described_
    else:
        return described_.reindex(columns=table.columns, copy=False)


def __get_counts(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> pd.DataFrame
    return __convert_to_df(table).count().to_frame().transpose()


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


# In old versions of pandas max_colwidth accepted only Int-s
def __get_tables_display_options():
    # type: () -> Tuple[None, Union[int, None]
    import sys
    if sys.version_info < (3, 0):
        return None, MAX_COLWIDTH_PYTHON_2
    return None, None
