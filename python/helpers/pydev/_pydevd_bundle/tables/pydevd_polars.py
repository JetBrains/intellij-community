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
    UNIQUE_VALUES_PERCENT = 60

    TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR = '__pydev_table_occurrences_count_next_column__'
    TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR = '__pydev_table_occurrences_count_next_value__'
    TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR = '__pydev_table_occurrences_count_dict__'


def get_value_occurrences_count(table):
    bin_counts = []

    def calculate_occurrences(col_name, column):
        col_type = column.dtype
        res = []
        column_visualisation_type = None
        if col_type == pl.Boolean and not column.is_null().any():
            column_visualisation_type = ColumnVisualisationType.HISTOGRAM
            counts = column.value_counts().sort(by=col_name).to_dict()
            res = add_custom_key_value_separator(zip(counts[col_name], counts["counts"]))

        elif column.is_numeric() and not column.is_null().all():
            column_visualisation_type = ColumnVisualisationType.HISTOGRAM
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

        elif col_type == pl.Boolean or col_type == pl.Object or col_type == pl.Utf8 or column.is_temporal() or column.is_null().all():
            value_counts = column.value_counts()
            all_values = len(column)
            if len(value_counts) / all_values * 100 <= ColumnVisualisationUtils.UNIQUE_VALUES_PERCENT:
                column_visualisation_type = ColumnVisualisationType.PERCENTAGE
                counts = value_counts[:ColumnVisualisationUtils.MAX_UNIQUE_VALUES - 1]
                top_values_counts = counts["counts"].apply(lambda count: int(count / all_values * 100))
                top_values = {label: count for label, count in zip(counts[col_name], top_values_counts)}
                others_count = value_counts[ColumnVisualisationUtils.MAX_UNIQUE_VALUES - 1:]["counts"].sum()
                top_values["Other"] = int(others_count / all_values * 100)
                res = add_custom_key_value_separator(top_values.items())

            else:
                column_visualisation_type = ColumnVisualisationType.UNIQUE
                counts = len(value_counts)

        if column_visualisation_type != ColumnVisualisationType.UNIQUE:
            counts = ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_VALUE_SEPARATOR.join(res)

        return str({column_visualisation_type: counts})

    def add_custom_key_value_separator(pairs_list):
        return [str(label) + ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_DICT_SEPARATOR + str(count) for label, count in pairs_list]

    if isinstance(table, pl.DataFrame):
        for col in table.columns:
            bin_counts.append(calculate_occurrences(col, table[col]))
    elif isinstance(table, pl.Series):
        bin_counts.append(calculate_occurrences(table.name, table))

    return ColumnVisualisationUtils.TABLE_OCCURRENCES_COUNT_NEXT_COLUMN_SEPARATOR.join(bin_counts)


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
