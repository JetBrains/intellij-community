#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import polars as pl

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH = 100000
pl_version_major, pl_version_minor, _ = pl.__version__.split(".")
pl_version_major, pl_version_minor = int(pl_version_major), int(pl_version_minor)
COUNT_COL_NAME = "counts" if pl_version_major == 0 and pl_version_minor < 20 else "count"
CSV_FORMAT_SEPARATOR = '~'


def get_type(table):
    # type: (str) -> str
    return str(type(table))


def get_shape(table):
    # type: (pl.DataFrame) -> str
    return str(table.shape)


def get_head(table):
    # type: (pl.DataFrame) -> str
    with __create_config():
        return table.head(1)._repr_html_()


def get_column_types(table):
    # type: (pl.DataFrame) -> str
    if type(table).__name__ == 'Series':
        return str(table.dtype)
    else:
        return TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(t) for t in table.dtypes])


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



# used by pydevd
def get_data(table, use_csv_serialization, start_index=None, end_index=None, format=None):
    # type: (pl.DataFrame, int, int) -> str
    with __create_config(format):
        if use_csv_serialization:
            float_precision = _get_float_precision(format)
            return __write_to_csv(__get_df_slice(table, start_index, end_index), float_precision=float_precision)
        return table[start_index:end_index]._repr_html_()


# used by DSTableCommands
def display_data_html(table, start, end):
    # type: (pl.DataFrame, int, int) -> None
    with __create_config():
        print(table[start:end]._repr_html_())


def display_data_csv(table, start, end):
    # type: (pl.DataFrame, int, int) -> None
    with __create_config():
        print(__write_to_csv(__get_df_slice(table, start, end)))


def __get_df_slice(table, start_index, end_index):
    if 'Series' in str(type(table)):
        return table[start_index:end_index].to_frame()
    return table[start_index:end_index]


def __create_config(format=None):
    # type: (Union[str, None]) -> pl.Config
    cfg = pl.Config()
    cfg.set_tbl_cols(-1)  # Unlimited
    cfg.set_tbl_rows(-1)  # Unlimited
    cfg.set_fmt_str_lengths(MAX_COLWIDTH)  # No option to set unlimited, so it's 100_000
    float_precision = _get_float_precision(format)
    if float_precision is not None:
        cfg.set_float_precision(float_precision)
    return cfg


def get_column_descriptions(table):
    # type: (Union[pl.DataFrame, pl.Series]) -> str
    described_results = __get_describe(table)

    if described_results is not None:
        return get_data(described_results, None, None)
    else:
        return ""


class ColumnVisualisationType:
    HISTOGRAM = "histogram"
    UNIQUE = "unique"
    PERCENTAGE = "percentage"


class ColumnVisualisationUtils:
    NUM_BINS = 20
    MAX_UNIQUE_VALUES = 3
    MAX_UNIQUE_VALUES_TO_SHOW_IN_VIS = 50

    TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR = '__pydev_table_occurrences_count_next_column__'
    TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR = '__pydev_table_occurrences_count_next_value__'
    TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR = '__pydev_table_occurrences_count_dict__'
    TABLE_OCCURRENCES_COUNT_OTHER = '__pydev_table_other__'


def get_value_occurrences_count(table):
    bin_counts = []
    if isinstance(table, pl.DataFrame):
        for col in table.columns:
            bin_counts.append(analyze_column(col, table[col]))
    elif isinstance(table, pl.Series):
        bin_counts.append(analyze_column(table.name, table))

    return ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR.join(bin_counts)


def analyze_column(col_name, column):
    col_type = column.dtype
    res = []
    column_visualisation_type = None
    if col_type == pl.Boolean and not column.is_null().any():
        column_visualisation_type, res = analyze_boolean_column(column, col_name)

    elif column.is_numeric() and not column.is_null().all():
        column_visualisation_type, res = analyze_numeric_column(column, col_name)

    elif col_type == pl.Boolean or col_type == pl.Object or col_type == pl.Utf8 or column.is_temporal() or column.is_null().all():
        column_visualisation_type, res = analyze_categorical_column(column, col_name)

    if column_visualisation_type != ColumnVisualisationType.UNIQUE:
        counts = ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR.join(res)
    else:
        counts = res

    return str({column_visualisation_type:counts})


def analyze_boolean_column(column, col_name):
    counts = column.value_counts().sort(by=col_name).to_dict()
    return ColumnVisualisationType.HISTOGRAM, add_custom_key_value_separator(zip(counts[col_name], counts[COUNT_COL_NAME]))


def analyze_categorical_column(column, col_name):
    all_values = len(column)
    if column.is_null().all():
        value_counts = pl.DataFrame({col_name: "None", COUNT_COL_NAME: all_values})
    else:
        value_counts = column.value_counts()

    # Sort in descending order to get values with max percent
    value_counts = value_counts.sort(COUNT_COL_NAME).reverse()

    if len(value_counts) <= 3 or len(value_counts) / all_values * 100 <= ColumnVisualisationUtils.MAX_UNIQUE_VALUES_TO_SHOW_IN_VIS:
        column_visualisation_type = ColumnVisualisationType.PERCENTAGE

        # If column contains <= 3 unique values no `Other` category is shown, but all of these values and their percentages
        num_unique_values_to_show_in_vis = ColumnVisualisationUtils.MAX_UNIQUE_VALUES - (0 if len(value_counts) == 3 else 1)
        counts = value_counts[:num_unique_values_to_show_in_vis]
        top_values_counts = counts[COUNT_COL_NAME].apply(lambda count: round(count / all_values * 100, 1))
        top_values = {label: count for label, count in zip(counts[col_name], top_values_counts)}
        if len(value_counts) == 3:
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = -1
        else:
            others_count = value_counts[num_unique_values_to_show_in_vis:][COUNT_COL_NAME].sum()
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = round(others_count / all_values * 100, 1)
        res = add_custom_key_value_separator(top_values.items())

    else:
        column_visualisation_type = ColumnVisualisationType.UNIQUE
        res = len(value_counts)
    return column_visualisation_type, res


def analyze_numeric_column(column, col_name):
    # handle np.NaN values, because they are not dropped with drop_nulls() the way they are
    column = column.fill_nan(None).drop_nulls()
    if column.is_float():
        # for float type we don't compute number of unique values because it's an
        # expensive operation, just take number of elements in a column
        unique_values = column.len()
    else:
        unique_values = column.n_unique()
    if unique_values > ColumnVisualisationUtils.NUM_BINS:
        import numpy as np

        def format_function(x):
            if x == int(x):
                return int(x)
            else:
                return round(x, 3)

        counts, bin_edges = np.histogram(column, bins=ColumnVisualisationUtils.NUM_BINS)
        # so the long dash will be correctly viewed both on Mac and Windows
        bin_labels = ['{} \u2014 {}'.format(format_function(bin_edges[i]), format_function(bin_edges[i + 1])) for i in range(ColumnVisualisationUtils.NUM_BINS)]
        res = add_custom_key_value_separator(zip(bin_labels, counts))
    else:
        raw_counts = column.value_counts().sort(by=col_name).to_dict()
        res = add_custom_key_value_separator(zip(raw_counts[col_name], raw_counts[COUNT_COL_NAME]))
    return ColumnVisualisationType.HISTOGRAM, res


def add_custom_key_value_separator(pairs_list):
    return [str(label) + ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR + str(count) for label, count in pairs_list]


def __get_describe(table):
    # type: (Union[pl.DataFrame, pl.Series]) -> Union[pl.DataFrame, None]
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
    except:
        return


def _get_float_precision(format):
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
