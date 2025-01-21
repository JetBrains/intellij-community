#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import pandas as pd
import typing

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH = 100000
CSV_FORMAT_SEPARATOR = '~'


def get_type(table):
    # type: (str) -> str
    return str(type(table))


# noinspection PyUnresolvedReferences
def get_shape(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> str
    return str(table.shape[0])


# noinspection PyUnresolvedReferences
def get_head(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> str
    return repr(__convert_to_df(table).head(1).to_html(notebook=True, max_cols=None))


# noinspection PyUnresolvedReferences
def get_column_types(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> str
    table = __convert_to_df(table)
    return str(table.index.dtype) + TABLE_TYPE_NEXT_VALUE_SEPARATOR + \
        TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(t) for t in table.dtypes])


# used by pydevd
# noinspection PyUnresolvedReferences
def get_data(table, use_csv_serialization, start_index=None, end_index=None, format=None):
    # type: (Union[pd.DataFrame, pd.Series], bool, int, int) -> str

    def convert_data_to_csv(data, format):
        return repr(__convert_to_df(data).to_csv(na_rep = "NaN", float_format=format, sep=CSV_FORMAT_SEPARATOR))

    def convert_data_to_html(data, format):
        return repr(__convert_to_df(data).to_html(notebook=True))

    if use_csv_serialization:
        computed_data = _compute_sliced_data(table, convert_data_to_csv, start_index, end_index, format)
    else:
        computed_data = _compute_sliced_data(table, convert_data_to_html, start_index, end_index, format)
    return computed_data


# used by DSTableCommands
# noinspection PyUnresolvedReferences
def display_data_csv(table, start_index, end_index):
    # type: (Union[pd.DataFrame, pd.Series], int, int) -> None
    def ipython_display(data, format):
        try:
            data = data.to_csv(na_rep = "NaN", sep=CSV_FORMAT_SEPARATOR, float_format=format)
        except AttributeError:
            pass
        print(__convert_to_df(data))
    _compute_sliced_data(table, ipython_display, start_index, end_index)


# used by DSTableCommands
# noinspection PyUnresolvedReferences
def display_data_html(table, start_index, end_index):
    # type: (Union[pd.DataFrame, pd.Series], int, int) -> None
    def ipython_display(data, format):
        from IPython.display import display
        display(__convert_to_df(data))
    _compute_sliced_data(table, ipython_display, start_index, end_index)


def __get_data_slice(table, start, end):
    return __convert_to_df(table).iloc[start:end]


def _compute_sliced_data(table, fun, start_index=None, end_index=None, format=None):
    # type: (Union[pd.DataFrame, pd.Series], function, int, int) -> str

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

def get_column_descriptions(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> str
    described_result = __get_describe(table)

    if described_result is not None:
        return get_data(described_result, None, None)
    else:
        return ""


def __get_describe(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> Union[pd.DataFrame, pd.Series, None]
    try:
        described_ = table.describe(percentiles=[.05, .25, .5, .75, .95],
                                    exclude=[np.complex64, np.complex128])
    except (TypeError, OverflowError, ValueError):
        return

    try:
        import geopandas
        if type(table) is geopandas.GeoSeries:
            return described_
    except ImportError:
        pass

    if type(table) is pd.Series:
        return described_
    else:
        return described_.reindex(columns=table.columns, copy=False)


class ColumnVisualisationType:
    HISTOGRAM = "histogram"
    UNIQUE = "unique"
    PERCENTAGE = "percentage"


class ColumnVisualisationUtils:
    NUM_BINS = 20
    MAX_UNIQUE_VALUES_TO_SHOW_IN_VIS = 3
    UNIQUE_VALUES_PERCENT = 50

    TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR = '__pydev_table_occurrences_count_next_column__'
    TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR = '__pydev_table_occurrences_count_next_value__'
    TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR = '__pydev_table_occurrences_count_dict__'
    TABLE_OCCURRENCES_COUNT_OTHER = '__pydev_table_other__'


def get_value_occurrences_count(table):
    df = __convert_to_df(table)
    bin_counts = []

    for _, column_data in df.items():
        column_visualisation_type, result = analyze_column(column_data)

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
    if len(column) == 0 or not isinstance(column.iloc[0], typing.Hashable):
        return None, "{}"

    value_counts = column.value_counts(dropna=False)
    all_values = len(column)
    vis_type = ColumnVisualisationType.PERCENTAGE
    if len(value_counts) <= 3 or len(value_counts) / all_values * 100 <= ColumnVisualisationUtils.UNIQUE_VALUES_PERCENT:
        # If column contains <= 3 unique values no `Other` category is shown, but all of these values and their percentages
        num_unique_values_to_show_in_vis = ColumnVisualisationUtils.MAX_UNIQUE_VALUES_TO_SHOW_IN_VIS - (0 if len(value_counts) == 3 else 1)

        top_values = value_counts.iloc[:num_unique_values_to_show_in_vis].apply(lambda count: round(count / all_values * 100, 1)).to_dict()
        if len(value_counts) == 3:
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = -1
        else:
            others_count = value_counts.iloc[num_unique_values_to_show_in_vis:].sum()
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = round(others_count / all_values * 100, 1)
        result = add_custom_key_value_separator(top_values.items())
    else:
        vis_type = ColumnVisualisationType.UNIQUE
        top_values = len(value_counts)
        result = top_values
    return vis_type, result


def analyze_numeric_column(column):
    if column.size <= ColumnVisualisationUtils.NUM_BINS:
        res = column.value_counts().sort_index().to_dict()
    else:
        def format_function(x):
            if x == int(x):
                return int(x)
            else:
                return round(x, 3)

        counts, bin_edges = np.histogram(column.dropna(), bins=ColumnVisualisationUtils.NUM_BINS)

        # so the long dash will be correctly viewed both on Mac and Windows
        bin_labels = ['{} \u2014 {}'.format(format_function(bin_edges[i]), format_function(bin_edges[i+1])) for i in range(ColumnVisualisationUtils.NUM_BINS)]
        bin_count_dict = {label: count for label, count in zip(bin_labels, counts)}
        res = bin_count_dict
    return add_custom_key_value_separator(res.items())


def add_custom_key_value_separator(pairs_list):
    return ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR.join(
        ['{}{}{}'.format(key, ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR, value) for key, value in pairs_list]
    )


# noinspection PyUnresolvedReferences
def __convert_to_df(table):
    # type: (Union[pd.DataFrame, pd.Series, pd.Categorical]) -> pd.DataFrame
    try:
        import geopandas
        if type(table) is geopandas.GeoSeries:
            return __series_to_df(table)
    except ImportError:
        pass

    if type(table) is pd.Series:
        return __series_to_df(table)
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
def __array_to_df(table):
    # type: (np.ndarray) -> pd.DataFrame
    return pd.DataFrame(table)


def __categorical_to_df(table):
    # type: (pd.Categorical) -> pd.DataFrame
    return pd.DataFrame(table)


# In old versions of pandas max_colwidth accepted only Int-s
def __get_tables_display_options():
    # type: () -> Tuple[None, Union[int, None], None]
    import sys
    if sys.version_info < (3, 0):
        return None, MAX_COLWIDTH, None
    try:
        if int(pd.__version__.split('.')[0]) < 1:
            return None, MAX_COLWIDTH, None
    except ImportError:
        pass
    return None, None, None
