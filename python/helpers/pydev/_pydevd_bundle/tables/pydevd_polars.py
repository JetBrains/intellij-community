#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import polars as pl

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH = 100000


def get_type(table):
    # type: (str) -> str
    return str(type(table))


def get_shape(table):
    # type: (pl.DataFrame) -> str
    return str(table.shape)


def get_head(table):
    # type: (pl.DataFrame) -> str
    with __create_config():
        return table.head()._repr_html_()


def get_column_types(table):
    # type: (pl.DataFrame) -> str
    if type(table).__name__ == 'Series':
        return str(table.dtype)
    else:
        return TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(t) for t in table.dtypes])


# used by pydevd, isDisplaySupported equals false
def get_data(table, start_index=None, end_index=None):
    # type: (pl.DataFrame, int, int) -> str
    with __create_config():
        return table[start_index:end_index]._repr_html_()


# used by DSTableCommands isDisplaySupported equals true
def display_data(table, start, end):
    # type: (pl.DataFrame, int, int) -> None
    with __create_config():
        print(table[start:end]._repr_html_())


def __create_config():
    # type: () -> pl.Config
    cfg = pl.Config()
    cfg.set_tbl_cols(-1)  # Unlimited
    cfg.set_fmt_str_lengths(MAX_COLWIDTH)  # No option to set unlimited, so it's 100_000
    return cfg


def get_column_descriptions(table):
    # type: (Union[pl.DataFrame, pl.Series]) -> str
    described_results = __get_describe(table)

    if described_results is not None:
        return get_data(described_results, None, None)
    else:
        return ""


# Polars compute NaN-s in describe. So, we don't need get_value_counts for Polars
def get_value_counts(table):
    # type: (Union[pl.DataFrame, pl.Series]) -> str
    return ""


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
    return ColumnVisualisationType.HISTOGRAM, add_custom_key_value_separator(zip(counts[col_name], counts["counts"]))


def analyze_categorical_column(column, col_name):
    all_values = len(column)
    if column.is_null().all():
        value_counts = pl.DataFrame({col_name: "None", "counts": all_values})
    else:
        value_counts = column.value_counts()

    # Sort in descending order to get values with max percent
    value_counts = value_counts.sort("counts").reverse()

    if len(value_counts) <= 3 or len(value_counts) / all_values * 100 <= ColumnVisualisationUtils.UNIQUE_VALUES_PERCENT:
        column_visualisation_type = ColumnVisualisationType.PERCENTAGE

        # If column contains <= 3 unique values no `Other` category is shown, but all of these values and their percentages
        num_unique_values = ColumnVisualisationUtils.MAX_UNIQUE_VALUES - (0 if len(value_counts) == 3 else 1)
        counts = value_counts[:num_unique_values]
        top_values_counts = counts["counts"].apply(lambda count: round(count / all_values * 100, 1))
        top_values = {label: count for label, count in zip(counts[col_name], top_values_counts)}
        if len(value_counts) == 3:
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = -1
        else:
            others_count = value_counts[ColumnVisualisationUtils.MAX_UNIQUE_VALUES - 1:]["counts"].sum()
            top_values[ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_OTHER] = round(others_count / all_values * 100, 1)
        res = add_custom_key_value_separator(top_values.items())

    else:
        column_visualisation_type = ColumnVisualisationType.UNIQUE
        res = len(value_counts)
    return column_visualisation_type, res


def analyze_numeric_column(column, col_name):
    column = column.drop_nulls()
    unique_values = column.n_unique()
    if unique_values > ColumnVisualisationUtils.NUM_BINS:
        counts, bin_edges = np.histogram(column, bins=ColumnVisualisationUtils.NUM_BINS)
        format_function = int if column.is_integer() else lambda x: round(x, 1)
        bin_labels = ['{} — {}'.format(format_function(bin_edges[i]), format_function(bin_edges[i + 1])) for i in range(ColumnVisualisationUtils.NUM_BINS)]
        res = add_custom_key_value_separator(zip(bin_labels, counts))
    else:
        counts = column.value_counts().sort(by=col_name).to_dict()
        res = add_custom_key_value_separator(zip(counts[col_name], counts["counts"]))
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
