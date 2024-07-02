#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import pandas as pd

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH_PYTHON_2 = 100000
BATCH_SIZE = 10000


def get_type(table):
    return str(type(table))


# noinspection PyUnresolvedReferences
def get_shape(table):
    return str(table.shape[0])


# noinspection PyUnresolvedReferences
def get_head(table):
    table = pd.concat(list(__convert_to_df(table)), ignore_index=True)
    return repr(table.head().to_html(notebook=True, max_cols=None))


# noinspection PyUnresolvedReferences
def get_column_types(table):
    table = pd.concat(list(__convert_to_df(table)), ignore_index=True)
    return str(table.index.dtype) + TABLE_TYPE_NEXT_VALUE_SEPARATOR + \
            TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(t) for t in table.dtypes])


# used by pydevd
# noinspection PyUnresolvedReferences
def get_data(table, start_index=None, end_index=None):

    def convert_data_to_html(data, max_cols):
        return repr(data.to_html(notebook=True, max_cols=max_cols))

    return _compute_sliced_data(table, convert_data_to_html, start_index, end_index)


# used by DSTableCommands
# noinspection PyUnresolvedReferences
def display_data(table, start_index, end_index):
    def ipython_display(data, max_cols):
        from IPython.display import display
        display(data)

    _compute_sliced_data(table, ipython_display, start_index, end_index)


def __get_data_slice(table, start, end):
    table = pd.concat(list(__convert_to_df(table)), ignore_index=True)
    return table.iloc[start:end]


def _compute_sliced_data(table, fun, start_index=None, end_index=None):
    max_cols, max_colwidth = __get_tables_display_options()

    _jb_max_cols = pd.get_option('display.max_columns')
    _jb_max_colwidth = pd.get_option('display.max_colwidth')

    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_colwidth', max_colwidth)

    if start_index is not None and end_index is not None:
        table = __get_data_slice(table, start_index, end_index)
    else:
        table = pd.concat(list(__convert_to_df(table)), ignore_index=True)

    data = fun(table, max_cols)

    pd.set_option('display.max_columns', _jb_max_cols)
    pd.set_option('display.max_colwidth', _jb_max_colwidth)

    return data


# In old versions of pandas max_colwidth accepted only Int-s
def __get_tables_display_options():
    import sys
    if sys.version_info < (3, 0):
        return None, MAX_COLWIDTH_PYTHON_2
    return None, None


# noinspection PyUnresolvedReferences
def __convert_to_df(table):
    try:
        import datasets
        if type(table) is datasets.arrow_dataset.Dataset:
            return __dataset_to_df(table)
    except ImportError as e:
        pass
    return table


def __dataset_to_df(dataset):
    try:
        return dataset.to_pandas(batched=True, batch_size=min(len(dataset), BATCH_SIZE))
    except ImportError as e:
        pass