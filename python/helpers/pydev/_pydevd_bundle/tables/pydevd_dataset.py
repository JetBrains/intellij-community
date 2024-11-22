#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import pandas as pd

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH_PYTHON_2 = 100000
BATCH_SIZE = 10000

CSV_FORMAT_SEPARATOR = '~'


def get_type(table):
    # type: (str) -> str
    return str(type(table))


# noinspection PyUnresolvedReferences
def get_shape(table):
     # type: (datasets.arrow_dataset.Dataset) -> str
    return str(table.shape[0])


# noinspection PyUnresolvedReferences
def get_head(table):
     # type: (datasets.arrow_dataset.Dataset) -> str
    return repr(__convert_to_df(table.select([0])).head(1).to_html(notebook=True))


# noinspection PyUnresolvedReferences
def get_column_types(table):
     # type: (datasets.arrow_dataset.Dataset) -> str
    table = __convert_to_df(table.select([0]))
    return str(table.index.dtype) + TABLE_TYPE_NEXT_VALUE_SEPARATOR + \
            TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(t) for t in table.dtypes])


# used by pydevd
# noinspection PyUnresolvedReferences
def get_data(table, use_csv_serialization, start_index=None, end_index=None, format=None):
     # type: (datasets.arrow_dataset.Dataset, int, int) -> str

    def convert_data_to_csv(data, format):
        return repr(data.to_csv(na_rep = "NaN", float_format=format, sep=CSV_FORMAT_SEPARATOR))

    def convert_data_to_html(data, format):
        return repr(data.to_html(notebook=True))

    if use_csv_serialization:
        computed_data = _compute_sliced_data(table, convert_data_to_csv, start_index, end_index, format)
    else:
        computed_data = _compute_sliced_data(table, convert_data_to_html, start_index, end_index, format)
    return computed_data


# used by DSTableCommands
# noinspection PyUnresolvedReferences
def display_data_csv(table, start_index, end_index):
     # type: (datasets.arrow_dataset.Dataset, int, int) -> None
    def ipython_display(data, format):
        try:
            data = data.to_csv(na_rep = "NaN", sep=CSV_FORMAT_SEPARATOR, float_format=format)
        except AttributeError:
            pass
        print(data)
    _compute_sliced_data(table, ipython_display, start_index, end_index)


# used by DSTableCommands
# noinspection PyUnresolvedReferences
def display_data_html(table, start_index, end_index):
    # type: (datasets.arrow_dataset.Dataset, int, int) -> None
    def ipython_display(data, format):
        from IPython.display import display
        display(data)
    _compute_sliced_data(table, ipython_display, start_index, end_index)


def __get_data_slice(table, start, end):
    # type: (datasets.arrow_dataset.Dataset, int, int) -> pd.DataFrame
    return __convert_to_df(table).iloc[start:end]


def _compute_sliced_data(table, fun, start_index=None, end_index=None, format=None):
    # type: (datasets.arrow_dataset.Dataset, function, int, int) -> str
    max_cols, max_colwidth, max_rows = __get_tables_display_options()

    _jb_max_cols = pd.get_option('display.max_columns')
    _jb_max_colwidth = pd.get_option('display.max_colwidth')
    _jb_max_rows = pd.get_option('display.max_rows')
    if format is not None:
        _jb_float_options = pd.get_option('display.float_format')

    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_rows', max_rows)
    pd.set_option('display.max_colwidth', max_colwidth)

    format_function = _define_format_function(format)
    if format_function is not None:
        pd.set_option('display.float_format', format_function)

    if start_index is not None and end_index is not None:
        table = __get_data_slice(table, start_index, end_index)
    else:
        table = __convert_to_df(table)

    data = fun(table, pd.get_option('display.float_format'))

    pd.set_option('display.max_columns', _jb_max_cols)
    pd.set_option('display.max_colwidth', _jb_max_colwidth)
    pd.set_option('display.max_rows', _jb_max_rows)
    if format is not None:
        pd.set_option('display.float_format', _jb_float_options)

    return data


def _define_format_function(format):
    # type: (Union[None, str]) -> Union[Callable, None]
    if format is None or format == 'null':
        return None

    if format.startswith("%"):
        return lambda x: format % x

    return None


# In old versions of pandas max_colwidth accepted only Int-s
def __get_tables_display_options():
    # type: () -> Tuple[None, Union[int, None], None]
    import sys
    if sys.version_info < (3, 0):
        return None, MAX_COLWIDTH_PYTHON_2, None
    try:
        import pandas as pd
        if int(pd.__version__.split('.')[0]) < 1:
            return None, MAX_COLWIDTH_PYTHON_2, None
    except ImportError:
        pass
    return None, None, None


# noinspection PyUnresolvedReferences
def __convert_to_df(table):
    # type: (datasets.arrow_dataset.Dataset) -> pd.DataFrame
    try:
        import datasets
        if type(table) is datasets.arrow_dataset.Dataset:
            return __dataset_to_df(table)
    except ImportError as e:
        pass
    return table


def __dataset_to_df(dataset):
    # type: (datasets.arrow_dataset.Dataset) -> pd.DataFrame
    try:
        dataset_as_df = list(dataset.to_pandas(batched=True, batch_size=min(len(dataset), BATCH_SIZE)))
        if len(dataset_as_df) > 1:
            return pd.concat(dataset_as_df, ignore_index=True)
        else:
            return dataset_as_df[0]
    except ImportError as e:
        pass