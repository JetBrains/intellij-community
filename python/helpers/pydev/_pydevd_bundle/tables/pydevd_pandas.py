#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import pandas as pd
import typing

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
    except (TypeError, OverflowError, ValueError):
        return

    if type(table) is pd.Series:
        return described_
    else:
        return described_.reindex(columns=table.columns, copy=False)


def __get_counts(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> pd.DataFrame
    return __convert_to_df(table).count().to_frame().transpose()


class ColumnVisualisationType:
    HISTOGRAM = "histogram"
    UNIQUE = "unique"
    PERCENTAGE = "percentage"

class ColumnVisualisationUtils:
    NUM_BINS = 5
    MAX_UNIQUE_VALUES = 3
    UNIQUE_VALUES_PERCENT = 50

    TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR = '__pydev_table_occurrences_count_next_column__'
    TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR = '__pydev_table_occurrences_count_next_value__'
    TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR = '__pydev_table_occurrences_count_dict__'
    TABLE_OCCURRENCES_COUNT_OTHER = '__pydev_table_other__'

def get_value_occurrences_count(table):
    df = __convert_to_df(table)
    bin_counts = []

    for col_name in df.columns:
        column_visualisation_type, result = analyze_column(df[col_name])

        bin_counts.append(str({column_visualisation_type:result}))
    return ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR.join(bin_counts)


def analyze_column(column):
    col_type = column.dtype

    if col_type == bool:
        return ColumnVisualisationType.HISTOGRAM, analyze_boolean_column(column)
    elif col_type.kind in ['O', 'S', 'U', 'M', 'm', 'c'] or column.isna().all():
        return analyze_categorical_column(column)
    elif col_type.kind in ['i', 'f', 'u']:
        return ColumnVisualisationType.HISTOGRAM, analyze_numeric_column(column)


def analyze_boolean_column(column):
    res = column.value_counts().sort_index().to_dict()
    return add_custom_key_value_separator(res.items())


def analyze_categorical_column(column):
    # Processing of unhashable types (lists, dicts, etc.).
    # In Polars these types are NESTED and can be processed separately, but in Pandas they are Objects
    if not isinstance(column.iloc[0], typing.Hashable):
        return None, "{}"

    value_counts = column.value_counts(dropna=False)
    all_values = len(column)
    vis_type = ColumnVisualisationType.PERCENTAGE
    if len(value_counts) <= 3 or len(value_counts) / all_values * 100 <= ColumnVisualisationUtils.UNIQUE_VALUES_PERCENT:
        # If column contains <= 3 unique values no `Other` category is shown, but all of these values and their percentages
        num_unique_values = ColumnVisualisationUtils.MAX_UNIQUE_VALUES - (0 if len(value_counts) == 3 else 1)

        top_values = value_counts.iloc[:num_unique_values].apply(lambda count: round(count / all_values * 100, 1)).to_dict()
        if len(value_counts) == 3:
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = -1
        else:
            others_count = value_counts.iloc[ColumnVisualisationUtils.MAX_UNIQUE_VALUES - 1:].sum()
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = round(others_count / all_values * 100, 1)
        result = add_custom_key_value_separator(top_values.items())
    else:
        vis_type = ColumnVisualisationType.UNIQUE
        top_values = len(value_counts)
        result = top_values
    return vis_type, result


def analyze_numeric_column(column):
    unique_values = column.nunique()
    if unique_values <= ColumnVisualisationUtils.NUM_BINS:
        res = column.value_counts().sort_index().to_dict()
    else:
        counts, bin_edges = np.histogram(column.dropna(), bins=ColumnVisualisationUtils.NUM_BINS)
        if column.dtype.kind == 'i':
            format_function = lambda x: int(x)
        else:
            format_function = lambda x: round(x, 1)

        bin_labels = ['{} — {}'.format(format_function(bin_edges[i]), format_function(bin_edges[i+1])) for i in range(ColumnVisualisationUtils.NUM_BINS)]
        bin_count_dict = {label: count for label, count in zip(bin_labels, counts)}
        res = bin_count_dict
    return add_custom_key_value_separator(res.items())


def add_custom_key_value_separator(pairs_list):
    return ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR.join(
        ['{}{}{}'.format(key, ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR, value) for key, value in pairs_list]
    )


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
