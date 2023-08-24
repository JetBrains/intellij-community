#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import polars as pl

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'


def get_type(table):
    # type: (str) -> str
    return str(type(table))


def get_shape(table):
    # type: (pl.DataFrame) -> str
    return str(table.shape)


def get_head(table, max_cols):
    # type: (pl.DataFrame, int) -> str
    with __create_config():
        return table.head()._repr_html_()


def get_column_types(table):
    # type: (pl.DataFrame) -> str
    if type(table).__name__ == 'Series':
        return str(table.dtype)
    else:
        return TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(t) for t in table.dtypes])


# used by pydevd, isDisplaySupported equals false
def get_data(table, max_cols, max_colwidth, start_index=None, end_index=None):
    # type: (pl.DataFrame, int, int, int, int) -> str
    with __create_config():
        return table[start_index:end_index]._repr_html_()


# used by DSTableCommands isDisplaySupported equals true
def display_data(table, max_cols, max_colwidth, start, end):
    # type: (pl.DataFrame, int, int, int, int) -> None
    with __create_config():
        print(table[start:end]._repr_html_())


def __create_config():
    # type: () -> pl.Config
    return pl.Config(fmt_str_lengths=1000, set_tbl_cols=-1)


def get_column_descriptions(table, max_cols, max_colwidth):
    # type: (Union[pl.DataFrame, pl.Series], int, int) -> str
    described_results = __get_describe(table)

    return get_data(described_results, max_cols, max_colwidth, None, None)


# Polars compute NaN-s in describe. So, we don't need get_value_counts for Polars
def get_value_counts(table, max_cols, max_colwidth):
    # type: (Union[pl.DataFrame, pl.Series], int, int) -> str
    return


def __get_describe(table):
    # type: (Union[pl.DataFrame, pl.Series]) -> pl.DataFrame
    if type(table) == pl.DataFrame and 'describe' in table.columns:
        import random
        random_suffix = ''.join([chr(random.randint(97, 122)) for _ in range(5)])
        described_df = table\
            .rename({'describe': 'describe_original_' + random_suffix})\
            .describe(percentiles=(0.05, 0.25, 0.5, 0.75, 0.95))
    else:
        described_df = table.describe(percentiles=(0.05, 0.25, 0.5, 0.75, 0.95))
    return described_df
