#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import pandas as pd
import typing
from collections import OrderedDict
import sys
if sys.version_info < (3, 0):
    from collections import Iterable
else:
    from collections.abc import Iterable

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH = 100000
CSV_FORMAT_SEPARATOR = '~'
DASH_SYMBOL = '\u2014'
UNSUPPORTED_KINDS = {"c", "V"}  # complex, void/raw
OBJECT_SAMPLE_LIMIT = 10


class InspectionResultsDict:
    KEY_INSPECTION_NAME = "inspection"

    KEY_STATUS = "executionStatus"
    VALUE_STATUS_SUCCESS = "SUCCESS"
    VALUE_STATUS_FAILED = "FAILED"

    KEY_IS_TRIGGERED = "isTriggered"
    VALUE_TRIGGERED_NO = "NO"
    VALUE_TRIGGERED_YES = "YES"

    KEY_DETAILS = "inspectionResultDetails"
    KEY_DETAILS_TYPE = "type"
    VALUE_DETAILS_TYPE_ALL = "All"
    VALUE_DETAILS_TYPE_PER_COLUMN = "PerColumn"

    KEY_DETAILS_VALUE = "value"


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
        computed_data = __compute_sliced_data(table, convert_data_to_csv, start_index, end_index, format)
    else:
        computed_data = __compute_sliced_data(table, convert_data_to_html, start_index, end_index, format)
    return computed_data


# used by DSTableCommands
# noinspection PyUnresolvedReferences
def display_data_html(table, start_index, end_index):
    # type: (Union[pd.DataFrame, pd.Series], int, int) -> None
    def ipython_display(data, format):
        from IPython.display import display, HTML
        display(HTML(__convert_to_df(data).to_html(notebook=True)))
    __compute_sliced_data(table, ipython_display, start_index, end_index)


# used by DSTableCommands
# noinspection PyUnresolvedReferences
def display_data_csv(table, start_index, end_index):
    # type: (Union[pd.DataFrame, pd.Series], int, int) -> None
    def ipython_display(data, format):
        try:
            data = data.to_csv(na_rep = "NaN", sep=CSV_FORMAT_SEPARATOR, float_format=format)
        except AttributeError:
            pass
        print(repr(__convert_to_df(data)))
    __compute_sliced_data(table, ipython_display, start_index, end_index)


def get_column_descriptions(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> str
    described_result = __get_describe(table)

    if described_result is not None:
        return get_data(described_result, None, None)
    else:
        return ""


def get_value_occurrences_count(table):
    import warnings
    df = __convert_to_df(table)
    bin_counts = []

    with warnings.catch_warnings():
        warnings.simplefilter("ignore")  # Suppress all
        for _, column_data in df.items():
            column_visualisation_type, result = __analyze_column(column_data)

            bin_counts.append(str({column_visualisation_type:result}))
    return ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR.join(bin_counts)


def get_inspection_none_count(table):
    def _calculate_none_count(cur_table):
        """Calculate missing values per column"""
        results_per_column = []
        for col, missing_count in cur_table.isna().sum().items():
            if missing_count > 0:
                results_per_column.append({
                    "columnName": col,
                    "value": str(missing_count)
                })

        is_triggered = len(results_per_column) > 0
        details = __create_per_column_details(results_per_column) if is_triggered else None
        return __create_success_result(is_triggered, details)

    return __execute_inspection(table, _calculate_none_count, "NONE_COUNT")


def get_inspection_duplicate_rows(table):
    def _calculate_duplicate_rows(cur_table):
        duplicate_rows_number = cur_table.duplicated().sum()
        is_triggered = duplicate_rows_number > 0
        details = __create_all_details(str(duplicate_rows_number)) if is_triggered else None
        return __create_success_result(is_triggered, details)

    return __execute_inspection(table, _calculate_duplicate_rows, "DUPLICATE_ROWS")


def get_inspection_outliers(table):
    def _calculate_outliers(cur_table):
        results_per_column = []
        for col in cur_table.columns:
            if pd.api.types.is_numeric_dtype(cur_table[col]):
                q1 = cur_table[col].quantile(0.25)
                q3 = cur_table[col].quantile(0.75)
                iqr = q3 - q1
                lower_bound = q1 - 1.5 * iqr
                upper_bound = q3 + 1.5 * iqr

                # Boolean mask for outliers
                mask = (cur_table[col] < lower_bound) | (cur_table[col] > upper_bound)
                outliers_count = cur_table[col][mask].count()

                if outliers_count > 0:
                    results_per_column.append({
                        "columnName": col,
                        "value": str(outliers_count)
                    })

        is_triggered = len(results_per_column) > 0
        details = __create_per_column_details(results_per_column) if is_triggered else None
        return __create_success_result(is_triggered, details)

    return __execute_inspection(table, _calculate_outliers, "OUTLIERS")


def get_inspection_constant_columns(table):
    def _calculate_constant_columns(cur_table):
        results_per_column = []
        for col in cur_table.columns:
            if cur_table[col].nunique(dropna=False) == 1:
                results_per_column.append({
                    "columnName": col,
                    "value": str(cur_table[col].iloc[0])
                })

        is_triggered = len(results_per_column) > 0
        details = __create_per_column_details(results_per_column) if is_triggered else None
        return __create_success_result(is_triggered, details)

    return __execute_inspection(table, _calculate_constant_columns, "CONSTANT_COLUMNS")


def __get_data_slice(table, start, end):
    return __convert_to_df(table).iloc[start:end]


def __compute_sliced_data(table, fun, start_index=None, end_index=None, format=None):
    # type: (Union[pd.DataFrame, pd.Series], function, Union[None, int], Union[None, int], Union[None, str]) -> str

    max_cols, max_colwidth, max_rows = __get_tables_display_options()

    _jb_max_cols = pd.get_option('display.max_columns')
    _jb_max_colwidth = pd.get_option('display.max_colwidth')
    _jb_max_rows = pd.get_option('display.max_rows')
    if format is not None:
        _jb_float_options = pd.get_option('display.float_format')

    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_rows', max_rows)
    pd.set_option('display.max_colwidth', max_colwidth)

    format_function = __define_format_function(format)
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


def __define_format_function(format):
    # type: (Union[None, str]) -> Union[Callable, None]
    if format is None or format == 'null':
        return None

    if type(format) == str and format.startswith("%"):
        return lambda x: format % x

    return None


def __analyze_column(column):
    col_type = column.dtype

    if __is_boolean(col_type):
        return ColumnVisualisationType.HISTOGRAM, __analyze_boolean_column(column)
    elif __is_categorical(column, col_type):
        return __analyze_categorical_column(column)
    elif __is_numeric(col_type):
        return ColumnVisualisationType.HISTOGRAM, __analyze_numeric_column(column)


def __is_boolean(col_type):
    return col_type == bool


def __is_categorical(column, col_type):
    return col_type.kind in ['O', 'S', 'U', 'M', 'm', 'c'] or column.isna().all() or col_type.kind is None


def __is_numeric(col_type):
    return col_type.kind in ['i', 'f', 'u']

def __analyze_boolean_column(column):
    res = column.value_counts().sort_index().to_dict(OrderedDict)
    return __add_custom_key_value_separator(res.items())


def __analyze_categorical_column(column):
    # Processing of unhashable types (lists, dicts, etc.).
    # In Polars these types are NESTED and can be processed separately, but in Pandas they are Objects
    if len(column) == 0 or not isinstance(column.iloc[0], typing.Hashable):
        return None, "{}"

    value_counts = column.value_counts(dropna=False, normalize=True, sort=True, ascending=False)
    all_values = len(column)
    vis_type = ColumnVisualisationType.PERCENTAGE
    if len(value_counts) <= 3 or float(len(value_counts)) / all_values * 100 <= ColumnVisualisationUtils.UNIQUE_VALUES_PERCENT:
        # If column contains <= 3 unique values no `Other` category is shown, but all of these values and their percentages
        num_unique_values_to_show_in_vis = ColumnVisualisationUtils.MAX_UNIQUE_VALUES_TO_SHOW_IN_VIS - (0 if len(value_counts) == 3 else 1)

        top_values = value_counts.iloc[:num_unique_values_to_show_in_vis].apply(lambda v_c_share: round(v_c_share * 100, 1)).to_dict(OrderedDict)
        if len(value_counts) == 3:
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = -1
        else:
            others_count = value_counts.iloc[num_unique_values_to_show_in_vis:].sum()
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = round(others_count * 100, 1)
        result = __add_custom_key_value_separator(top_values.items())
    else:
        vis_type = ColumnVisualisationType.UNIQUE
        top_values = len(value_counts)
        result = top_values
    return vis_type, result


def __analyze_numeric_column(column):
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
        bin_labels = ['{} {} {}'.format(format_function(bin_edges[i]), DASH_SYMBOL, format_function(bin_edges[i+1])) for i in range(ColumnVisualisationUtils.NUM_BINS)]
        bin_count_dict = {label: count for label, count in zip(bin_labels, counts)}
        res = bin_count_dict
    return __add_custom_key_value_separator(res.items())


def __add_custom_key_value_separator(pairs_list):
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


def __get_tables_display_options():
    # type: () -> Tuple[None, Union[int, None], None]
    try:
        # In pandas versions earlier than 1.0, max_colwidth must be set as an integer
        if int(pd.__version__.split('.')[0]) < 1:
            return None, MAX_COLWIDTH, None
    except ImportError:
        pass
    return None, None, None


def __is_iterable(element):
    # type: (any) -> bool
    return isinstance(element, Iterable)


def __is_string(element):
    # type: (any) -> bool
    return isinstance(element, str)


def __should_skip_describe(element):
    # type: (any) -> bool
    if __is_string(element):
        return False
    if __is_iterable(element):
        return True
    return False

def __is_summarizable(series):
    # type: (pd.Series) -> bool
    kind = series.dtype.kind

    if kind in UNSUPPORTED_KINDS:
        return False

    # For object dtype, sample some values to check for lists or unstructured types
    if kind == "O":
        sample = series.dropna().head(OBJECT_SAMPLE_LIMIT)
        if sample.map(lambda x: __should_skip_describe(x)).any():
            return False

    return True

def __get_describe_dataframe(table):
    # type: (pd.DataFrame) -> pd.DataFrame
    describe_results = []
    for column_name in table.columns:
        series = table[column_name]
        if __is_summarizable(series):
            describe_results.append(__get_describe_series(series))
        else:
            describe_results.append(__get_dummy_describe_series(series))

    return pd.concat(describe_results, axis=1)


def __get_describe_series(series):
    # type: (pd.Series) -> pd.Series
    try:
        return series.describe(percentiles=[.05, .25, .5, .75, .95])
    except:
        return __get_dummy_describe_series(series)


def __get_dummy_describe_series(series):
    # type: (pd.Series) -> pd.Series
    manual_data = {"count": series.notna().count()}
    return pd.Series(data = manual_data, index=["count"], name=series.name)


def __get_describe(table):
    # type: (Union[pd.DataFrame, pd.Series]) -> Union[pd.DataFrame, pd.Series, None]
    try:
        if isinstance(table, pd.DataFrame):
            return __get_describe_dataframe(table)
        else:
            if __is_summarizable(table):
                return __get_describe_series(table)
            else:
                return __get_dummy_describe_series(table)
    except:
        return None


def __serialize_in_json(result_in_dict, inspection_name):
    try:
        import json
        result_in_dict[InspectionResultsDict.KEY_INSPECTION_NAME] = inspection_name
        return json.dumps(result_in_dict)
    except:
        return '{"%s": "%s" "%s": "%s"}' % (InspectionResultsDict.KEY_INSPECTION_NAME, inspection_name, InspectionResultsDict.KEY_STATUS, InspectionResultsDict.VALUE_STATUS_FAILED)


def __execute_inspection(table, inspection_func, inspection_name):
    """Execute inspection with error handling"""
    try:
        result = inspection_func(table)
        return __serialize_in_json(result, inspection_name)
    except:
        failed_result = __create_failed_result()
        return __serialize_in_json(failed_result, inspection_name)


def __create_success_result(is_triggered, details=None):
     is_triggered_value = InspectionResultsDict.VALUE_TRIGGERED_YES if is_triggered else InspectionResultsDict.VALUE_TRIGGERED_NO
     result = {
         InspectionResultsDict.KEY_STATUS: InspectionResultsDict.VALUE_STATUS_SUCCESS,
         InspectionResultsDict.KEY_IS_TRIGGERED: is_triggered_value
     }
     if details:
         result[InspectionResultsDict.KEY_DETAILS] = details
     return result


def __create_failed_result():
    return {
        InspectionResultsDict.KEY_STATUS: InspectionResultsDict.VALUE_STATUS_FAILED,
        InspectionResultsDict.KEY_IS_TRIGGERED: InspectionResultsDict.VALUE_TRIGGERED_NO
    }


def __create_per_column_details(results_per_column):
    return {
        InspectionResultsDict.KEY_DETAILS_TYPE: InspectionResultsDict.VALUE_DETAILS_TYPE_PER_COLUMN,
        InspectionResultsDict.KEY_DETAILS_VALUE: results_per_column
    }

def __create_all_details(value):
    return {
        InspectionResultsDict.KEY_DETAILS_TYPE: InspectionResultsDict.VALUE_DETAILS_TYPE_ALL,
        InspectionResultsDict.KEY_DETAILS_VALUE: value
    }