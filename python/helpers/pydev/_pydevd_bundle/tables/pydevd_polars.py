#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    except TypeError:
        return
