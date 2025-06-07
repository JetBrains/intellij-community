#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import polars as pl

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH = 100000
pl_version_major, pl_version_minor, _ = pl.__version__.split(".")
pl_version_major, pl_version_minor = int(pl_version_major), int(pl_version_minor)
COUNT_COL_NAME = "counts" if pl_version_major == 0 and pl_version_minor < 20 else "count"
CSV_FORMAT_SEPARATOR = '~'
DASH_SYMBOL = '\u2014'


class ColumnVisualisationType:
    HISTOGRAM = "histogram"
    UNIQUE = "unique"
    PERCENTAGE = "percentage"


class ColumnVisualisationUtils:
    NUM_BINS = 20
    MAX_UNIQUE_VALUES = 3
    MAX_UNIQUE_VALUES_TO_SHOW_IN_VIS = 50
    MAX_VALUES_LENGTH = 100

    TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR = '__pydev_table_occurrences_count_next_column__'
    TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR = '__pydev_table_occurrences_count_next_value__'
    TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR = '__pydev_table_occurrences_count_dict__'
    TABLE_OCCURRENCES_COUNT_OTHER = '__pydev_table_other__'


def get_type(table):
    # type: (Union[pl.Series, pl.DataFrame]) -> str
    return str(type(table))


def get_shape(table):
    # type: (Union[pl.Series, pl.DataFrame]) -> str
    return str(table.shape)


def get_head(table):
    # type: (Union[pl.Series, pl.DataFrame]) -> str
    with __create_config():
        return table.head(1)._repr_html_()


def get_column_types(table):
    # type: (Union[pl.Series, pl.DataFrame]) -> str
    if type(table) == pl.Series:
        return str(table.dtype)
    else:
        return TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(t) for t in table.dtypes])


# used by pydevd
def get_data(table, use_csv_serialization, start_index=None, end_index=None, format=None):
    # type: (Union[pl.Series, pl.DataFrame], int, int) -> str
    with __create_config(format):
        if use_csv_serialization:
            float_precision = __get_float_precision(format)
            return __write_to_csv(__get_df_slice(table, start_index, end_index), float_precision=float_precision)
        return table[start_index:end_index]._repr_html_()


# used by DSTableCommands
def display_data_html(table, start_index, end_index):
    # type: (Union[pl.Series, pl.DataFrame], int, int) -> None
    with __create_config():
        print(table[start_index:end_index]._repr_html_())


def display_data_csv(table, start_index, end_index):
    # type: (Union[pl.Series, pl.DataFrame], int, int) -> None
    with __create_config():
        print(repr(__write_to_csv(__get_df_slice(table, start_index, end_index))))


def get_column_descriptions(table):
    # type: (Union[pl.Series, pl.DataFrame]) -> str
    described_results = __get_describe(table)

    if described_results is not None:
        return get_data(described_results, None, None)
    else:
        return ""


def get_value_occurrences_count(table):
    # type: (Union[pl.Series, pl.DataFrame]) -> str
    bin_counts = []
    if type(table) == pl.DataFrame:
        for col in table.columns:
            bin_counts.append(__analyze_column(col, table[col]))
    elif type(table) == pl.Series:
        bin_counts.append(__analyze_column(table.name, table))

    return ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR.join(bin_counts)


def __analyze_column(col_name, column):
    col_type = column.dtype
    res = []
    column_visualisation_type = None

    if __is_boolean(column, col_type):
        column_visualisation_type, res = __analyze_boolean_column(column, col_name)

    elif __is_numeric(column, col_type):
        column_visualisation_type, res = __analyze_numeric_column(column, col_name)

    else:
        column_visualisation_type, res = __analyze_categorical_column(column, col_name)

    if column_visualisation_type != ColumnVisualisationType.UNIQUE:
        counts = ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR.join(res)
    else:
        counts = res

    return str({column_visualisation_type:counts})


def __is_boolean(column, col_type):
    # (pl.Series, pl.DataType) -> bool
    return col_type == pl.Boolean and not column.is_null().any()


def __is_numeric(column, col_type):
    # (pl.Series, pl.DataType) -> bool
    return __is_series_numeric(column) and not column.is_null().all()


def __analyze_boolean_column(column, col_name):
    counts = column.value_counts().sort(by=col_name).to_dict()
    return ColumnVisualisationType.HISTOGRAM, __add_custom_key_value_separator(zip(counts[col_name], counts[COUNT_COL_NAME]))


def __analyze_categorical_column(column, col_name):
    all_values = column.shape[0]
    if column.is_null().all():
        value_counts = pl.DataFrame({col_name: "None", COUNT_COL_NAME: all_values})
    else:
        value_counts = column.value_counts()

    # Sort in descending order to get values with max percent
    value_counts = value_counts.sort(COUNT_COL_NAME).reverse()

    if len(value_counts) <= ColumnVisualisationUtils.MAX_UNIQUE_VALUES or len(value_counts) / all_values * 100 <= ColumnVisualisationUtils.MAX_UNIQUE_VALUES_TO_SHOW_IN_VIS:
        column_visualisation_type = ColumnVisualisationType.PERCENTAGE

        # If column contains <= 3 unique values no `Other` category is shown, but all of these values and their percentages
        num_unique_values_to_show_in_vis = ColumnVisualisationUtils.MAX_UNIQUE_VALUES - (0 if len(value_counts) == 3 else 1)
        counts = value_counts[:num_unique_values_to_show_in_vis]
        counts = counts.with_columns(((pl.col(COUNT_COL_NAME) / all_values * 100).round(1)).alias(COUNT_COL_NAME))
        top_values = {}
        for label, count in zip(counts[col_name], counts[COUNT_COL_NAME]):
            # we should process separately a case with dtype == pl.List
            if type(label) == pl.Series or column.dtype == pl.List:
                label_values = label.to_list()
                label_values_in_str = str(label_values)
                top_values[label_values_in_str[:ColumnVisualisationUtils.MAX_VALUES_LENGTH]] = count
            else:
                label_in_str = str(label)
                top_values[label_in_str[:ColumnVisualisationUtils.MAX_VALUES_LENGTH]] = count
        if len(value_counts) == ColumnVisualisationUtils.MAX_UNIQUE_VALUES:
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = -1
        else:
            others_count = value_counts[num_unique_values_to_show_in_vis:][COUNT_COL_NAME].sum()
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = round(others_count / all_values * 100, 1)
        res = __add_custom_key_value_separator(top_values.items())

    else:
        column_visualisation_type = ColumnVisualisationType.UNIQUE
        res = len(value_counts)
    return column_visualisation_type, res


def __analyze_numeric_column(column, col_name):
    # handle np.NaN values, because they are not dropped with drop_nulls() the way they are
    column = column.fill_null(strategy="min")
    if column.shape[0] <= ColumnVisualisationUtils.NUM_BINS:
        raw_counts = column.value_counts().sort(by=col_name).to_dict(as_series=False)
        res = __add_custom_key_value_separator(zip(raw_counts[col_name], raw_counts[COUNT_COL_NAME]))
    else:
        import numpy as np

        def format_function(x):
            if x == int(x):
                return int(x)
            else:
                return round(x, 3)

        counts, bin_edges = np.histogram(column, bins=ColumnVisualisationUtils.NUM_BINS)
        # so the long dash will be correctly viewed both on Mac and Windows
        bin_labels = ['{} {} {}'.format(format_function(bin_edges[i]), DASH_SYMBOL, format_function(bin_edges[i + 1])) for i in range(ColumnVisualisationUtils.NUM_BINS)]
        res = __add_custom_key_value_separator(zip(bin_labels, counts))

    return ColumnVisualisationType.HISTOGRAM, res


def __get_df_slice(table, start_index, end_index):
    # type: (Union[pl.Series, pl.DataFrame], int, int) -> pl.DataFrame
    if type(table) == pl.Series:
        return table[start_index:end_index].to_frame()
    return table[start_index:end_index]


def __write_to_csv(table, null_value="null", float_precision=None):
    def serialize_nested(value, null_value="null", float_precision=None):
        if value is None:
            return null_value
        elif isinstance(value, float) and float_precision is not None:
            return "{:.{}f}".format(value, float_precision)
        elif isinstance(value, dict):
            return "{" + ", ".join("{}: {}".format(k, serialize_nested(v, null_value, float_precision)) for k, v in value.items()) + "}"
        elif isinstance(value, list):
            return "[" + ", ".join(serialize_nested(v, null_value, float_precision) for v in value) + "]"
        else:
            return str(value)

    lines = []
    lines.append(CSV_FORMAT_SEPARATOR.join(table.columns))
    for row in table.rows():
        line = []
        for value in row:
            line.append(serialize_nested(value, null_value, float_precision))
        lines.append(CSV_FORMAT_SEPARATOR.join(line))
    return "\n".join(lines)


def __create_config(format=None):
    # type: (Union[str, None]) -> pl.Config
    cfg = pl.Config()
    cfg.set_tbl_cols(-1)  # Unlimited
    cfg.set_tbl_rows(-1)  # Unlimited
    cfg.set_fmt_str_lengths(MAX_COLWIDTH)  # No option to set unlimited, so it's 100_000
    float_precision = __get_float_precision(format)
    if float_precision is not None and hasattr(cfg, 'set_float_precision'):
        cfg.set_float_precision(float_precision)
    return cfg


def __add_custom_key_value_separator(pairs_list):
    return [str(label) + ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR + str(count) for label, count in pairs_list]


def __get_describe(table):
    # type: (Union[pl.Series, pl.DataFrame]) -> Union[pl.DataFrame, None]
    try:
        if type(table) == pl.DataFrame and 'describe' in table.columns:
            import random
            random_suffix = ''.join([chr(random.randint(97, 122)) for _ in range(5)])
            described_df = table\
                .rename({'describe': 'describe_original_' + random_suffix})\
                .describe(percentiles=(0.05, 0.25, 0.5, 0.75, 0.95))
        else:
            described_df = table.describe(percentiles=(0.05, 0.25, 0.5, 0.75, 0.95))
        return described_df
    # If DataFrame/Series have unsupported type for describe
    # then Polars will raise TypeError exception. We should catch them.
    except Exception as e:
        return


def __get_float_precision(format):
    # type: (Union[str, None]) -> Union[int, None]
    if isinstance(format, str):
        if format.startswith("%") and format.endswith("f"):
            start = format.find('%.') + 2
            end = format.find('f')

            if start < end:
                try:
                    precision = int(format[start:end])
                    return precision
                except:
                    pass

    return None


def __is_series_numeric(column):
    """
    Determines if the given column is numeric based on the version of the polars
    library being used.

    For polars major version 0, the method checks if the column is numeric using
    the `is_numeric` method directly on the column. For later versions, it checks
    the `is_numeric` method on the column's data type.
    """
    if pl_version_major == 0:
        return column.is_numeric()
    else:
        return column.dtype.is_numeric()
