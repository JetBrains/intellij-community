#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import io
import numpy as np

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH = 100000

ONE_DIM, TWO_DIM, WITH_TYPES = range(3)
NP_ROWS_TYPE = "int64"

CSV_FORMAT_SEPARATOR = '~'

is_pd = False
try:
    import pandas as pd
    is_pd = True
except:
    pass


def get_type(table):
    # type: (np.ndarray) -> str
    return str(type(table))


def get_shape(table):
    # type: (np.ndarray) -> str
    if table.dtype.names is not None:
        return str((table.shape[0], len(table.dtype.names)))
    if table.ndim == 1:
        return str((table.shape[0], 1))
    elif table.ndim == 0:
        return str((0, 0))
    else:
        return str((table.shape[0], table.shape[1]))


def get_head(table):
    # type: (np.ndarray) -> str
    column_names = table.dtype.names
    if column_names:
        return TABLE_TYPE_NEXT_VALUE_SEPARATOR.join([str(column_names[i]) for i in range(len(column_names))])
    return "None"


def get_column_types(table):
    # type: (np.ndarray) -> str
    if table.ndim == 0:
        return ""
    table = __create_table(table[:1])
    try:
        cols_types = [str(t) for t in table.dtypes] if is_pd else table.get_cols_types()
    except AttributeError:
        cols_types = table.get_cols_types()

    return NP_ROWS_TYPE + TABLE_TYPE_NEXT_VALUE_SEPARATOR + \
        TABLE_TYPE_NEXT_VALUE_SEPARATOR.join(cols_types)


def get_data(table, use_csv_serialization, start_index=None, end_index=None, format=None):
    # type: (Union[np.ndarray, dict], int, int) -> str
    def convert_data_to_html(data, format):
        return repr(__create_table(data, start_index, end_index, format).to_html(notebook=True))

    def convert_data_to_csv(data, format):
        return repr(__create_table(data, start_index, end_index, format).to_csv(na_rep ="None", float_format=format, sep=CSV_FORMAT_SEPARATOR))

    if use_csv_serialization:
        computed_data = __compute_data(table, convert_data_to_csv, format)
    else:
        computed_data = __compute_data(table, convert_data_to_html, format)
    return computed_data


def display_data_html(table, start_index=None, end_index=None):
    # type: (np.ndarray, int, int) -> None
    def ipython_display(data, format):
        from IPython.display import display, HTML
        display(HTML(__create_table(data, start_index, end_index).to_html(notebook=True)))

    __compute_data(table, ipython_display)


def display_data_csv(table, start_index=None, end_index=None):
    # type: (np.ndarray, int, int) -> None
    def ipython_display(data, format):
        print(repr(__create_table(data, start_index, end_index).to_csv(na_rep ="None", sep=CSV_FORMAT_SEPARATOR, float_format=format)))

    __compute_data(table, ipython_display)


def remove_nones_values(array_part, na_rep):
    if np.issubdtype(array_part.dtype, np.number):
        array_part_without_nones = np.where(array_part == None, np.nan, array_part)
    else:
        array_part_without_nones = np.where(array_part == None, na_rep, array_part)
    return array_part_without_nones


class _NpTable:
    def __init__(self, np_array, format=None):
        self.array = np_array
        self.type = self.get_array_type()
        self.indexes = None
        self.format = format

    def get_array_type(self):
        col_type = self.array.dtype

        if len(col_type) != 0:
            return WITH_TYPES

        if self.array.ndim > 1:
            return TWO_DIM

        return ONE_DIM

    def get_cols_types(self):
        col_type = self.array.dtype

        if self.type == ONE_DIM:
            # [1, 2, 3] -> [int]
            return [str(col_type)]

        if self.type == WITH_TYPES:
            # ([(10, 3.14), (20, 2.71)], dtype=[("ci", "i4"), ("cf", "f4")]) -> [int, float]
            return [str(col_type[i]) for i in range(len(col_type))]  # is not iterable

        # [[1, 2], [3, 4]] -> [int, int]
        return [str(col_type) for _ in range(len(self.array[0]))]

    def head(self, num_rows):
        if self.array.shape[0] < 6:
            return self

        return _NpTable(self.array[:5]).sort()

    def to_html(self, notebook):
        html = ['<table class="dataframe">\n']

        # columns names
        html.append('<thead>\n'
                    '<tr style="text-align: right;">\n'
                    '<th></th>\n')
        html += self.__collect_cols_names()
        html.append('</tr>\n'
                    '</thead>\n')

        # tbody
        html += self.__collect_values(None)

        html.append('</table>\n')

        return "".join(html)

    def __collect_cols_names(self):
        if self.type == ONE_DIM:
            return ['<th>0</th>\n']

        if self.type == WITH_TYPES:
            columns_names = self.array.dtype.names
            return ['<th>{}</th>\n'.format(str(columns_names[i])) for i in range(len(columns_names))]

        return ['<th>{}</th>\n'.format(i) for i in range(len(self.array[0]))]

    def __collect_values(self, max_cols):
        html = ['<tbody>\n']
        rows = self.array.shape[0]
        for row_num in range(rows):
            html.append('<tr>\n')
            html.append('<th>{}</th>\n'.format(int(self.indexes[row_num])))
            if self.type == ONE_DIM:
                if self.format is not None and self.array[row_num] is not None and self.array[row_num] == self.array[row_num]:
                    try:
                        value = self.format % self.array[row_num]
                    except Exception as _:
                        value = self.array[row_num]
                else:
                    value = self.array[row_num]
                html.append('<td>{}</td>\n'.format(value))
            else:
                cols = len(self.array[0])
                max_cols = cols if max_cols is None else min(max_cols, cols)
                for col_num in range(max_cols):
                    if self.format is not None and self.array[row_num][col_num] is not None and self.array[row_num][col_num] == self.array[row_num][col_num]:
                        try:
                            value = self.format % self.array[row_num][col_num]
                        except Exception as _:
                            value = self.array[row_num][col_num]
                    else:
                        value = self.array[row_num][col_num]
                    html.append('<td>{}</td>\n'.format(value))
            html.append('</tr>\n')
        html.append('</tbody>\n')
        return html

    def to_csv(self, na_rep="None", float_format=None, sep=CSV_FORMAT_SEPARATOR):
        csv_stream = io.StringIO()
        if self.array.dtype.names is not None:
            np_array_without_nones = []
            for field in self.array.dtype.names:
                np_array_without_nones.append(remove_nones_values(self.array[str(field)], na_rep))
            np_array_without_nones = np.column_stack(np_array_without_nones)
        else:
            np_array_without_nones = remove_nones_values(self.array, na_rep)
        if float_format is None or float_format == 'null':
            float_format = "%s"

        np.savetxt(csv_stream, np_array_without_nones, delimiter=sep, fmt=float_format)
        csv_string = csv_stream.getvalue()
        csv_rows_with_index = self.__insert_index_at_rows_begging_csv(csv_string)

        col_names = self.__collect_col_names_csv()
        return col_names + "\n" + csv_rows_with_index

    def __insert_index_at_rows_begging_csv(self, csv_string):
        # type: (str) -> str
        csv_rows = csv_string.split('\n')
        csv_rows_with_index = []
        for row_index in range(self.array.shape[0]):
            csv_rows_with_index.append(str(row_index) + CSV_FORMAT_SEPARATOR + csv_rows[row_index])
        return "\n".join(csv_rows_with_index)

    def __collect_col_names_csv(self):
        if self.type == ONE_DIM:
            return '{}0'.format(CSV_FORMAT_SEPARATOR)

        if self.type == WITH_TYPES:
            return CSV_FORMAT_SEPARATOR + CSV_FORMAT_SEPARATOR.join(['{}'.format(name) for name in self.array.dtype.names])

        # TWO_DIM
        return CSV_FORMAT_SEPARATOR + CSV_FORMAT_SEPARATOR.join(['{}'.format(i) for i in range(self.array.shape[1])])


    def slice(self, start_index=None, end_index=None):
        if end_index is not None and start_index is not None:
            self.array = self.array[start_index:end_index]
            self.indexes = self.indexes[start_index:end_index]

        return self

    def sort(self, sort_keys=None):
        self.indexes = np.arange(self.array.shape[0])
        if sort_keys is None:
            return self

        cols, orders = sort_keys
        if 0 in cols:
            return self.__sort_by_index(True in orders)

        if self.type == ONE_DIM:
            extended = np.column_stack((self.indexes, self.array))
            sort_extended = extended[:, 1].argsort()
            if False in orders:
                sort_extended = sort_extended[::-1]
            result = extended[sort_extended]
            self.array = result[:, 1]
            self.indexes = result[:, 0]
            return self

        if self.type == WITH_TYPES:
            new_dt = np.dtype([('_pydevd_i', 'i8')] + self.array.dtype.descr)
            extended = np.zeros(self.array.shape, dtype=new_dt)
            extended['_pydevd_i'] = list(range(self.array.shape[0]))
            for col in self.array.dtype.names:
                extended[col] = self.array[col]

            column_names = self.array.dtype.names
            for i in range(len(cols) - 1, -1, -1):
                name = column_names[cols[i] - 1]
                sort = extended[name].argsort(kind='stable')
                extended = extended[sort if orders[i] else sort[::-1]]
            self.indexes = extended['_pydevd_i']
            for col in self.array.dtype.names:
                self.array[col] = extended[col]
            return self

        extended = np.insert(self.array, 0, self.indexes, axis=1)
        for i in range(len(cols) - 1, -1, -1):
            sort = extended[:, cols[i]].argsort(kind='stable')
            extended = extended[sort if orders[i] else sort[::-1]]
        self.indexes = extended[:, 0]
        self.array = extended[:, 1:]
        return self

    def __sort_by_index(self, order):
        if order:
            return self
        self.array = self.array[::-1]
        self.indexes = self.indexes[::-1]
        return self


def __sort_df(dataframe, sort_keys):
    if sort_keys is None:
        return dataframe

    cols, orders = sort_keys
    if 0 in cols:
        if len(cols) == 1:
            return dataframe.sort_index(ascending=orders[0])
        return dataframe.sort_index(level=cols, ascending=orders)
    sort_by = list(map(lambda c: dataframe.columns[c - 1], cols))
    return dataframe.sort_values(by=sort_by, ascending=orders)


def __create_table(command, start_index=None, end_index=None, format=None):
    sort_keys = None

    if type(command) is dict:
        np_array = command['data']
        sort_keys = command['sort_keys']
    else:
        np_array = command

    if is_pd:
        sorted_df = __sort_df(pd.DataFrame(np_array), sort_keys)
        if start_index is not None and end_index is not None:
            sorted_df_slice = sorted_df.iloc[start_index:end_index]
            # to apply "format" we should not have None inside DFs
            try:
                import warnings
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore")
                    sorted_df_slice = sorted_df_slice.fillna("None")
            except Exception as _:
                pass
            return sorted_df_slice
        return sorted_df

    return _NpTable(np_array, format=format).sort(sort_keys).slice(start_index,
                                                                   end_index)


def __compute_data(arr, fun, format=None):
    is_sort_command = type(arr) is dict
    data = arr['data'] if is_sort_command else arr

    jb_max_cols, jb_max_colwidth, jb_max_rows, jb_float_options = None, None, None, None
    if is_pd:
        jb_max_cols, jb_max_colwidth, jb_max_rows, jb_float_options = __set_pd_options(format)

    if is_sort_command:
        arr['data'] = data
        data = arr

    format = pd.get_option('display.float_format') if is_pd else format

    data = fun(data, format)

    if is_pd:
        __reset_pd_options(jb_max_cols, jb_max_colwidth, jb_max_rows, jb_float_options)

    return data


def __get_tables_display_options():
    # type: () -> Tuple[None, Union[int, None], None]
    try:
        import pandas as pd
        # In pandas versions earlier than 1.0, max_colwidth must be set as an integer
        if int(pd.__version__.split('.')[0]) < 1:
            return None, MAX_COLWIDTH, None
    except Exception:
        pass
    return None, None, None


def __set_pd_options(format):
    max_cols, max_colwidth, max_rows = __get_tables_display_options()
    _jb_float_options = None

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

    return _jb_max_cols, _jb_max_colwidth, _jb_max_rows, _jb_float_options


def __reset_pd_options(max_cols, max_colwidth, max_rows, float_format):
    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_colwidth', max_colwidth)
    pd.set_option('display.max_rows', max_rows)
    if float_format is not None:
        pd.set_option('display.float_format', float_format)


def __define_format_function(format):
    # type: (Union[None, str]) -> Union[Callable, None]
    if format is None or format == 'null':
        return None

    if type(format) == str and format.startswith("%"):
        return lambda x: format % x
    else:
        return None
