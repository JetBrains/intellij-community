#  Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import numpy as np
import io
try:
    import tensorflow as tf
except ImportError:
    pass
try:
    import torch
except ImportError:
    pass

TABLE_TYPE_NEXT_VALUE_SEPARATOR = '__pydev_table_column_type_val__'
MAX_COLWIDTH = 100000

ONE_DIM, TWO_DIM = range(2)
NP_ROWS_TYPE = "int64"

CSV_FORMAT_SEPARATOR = '~'

is_pd = False
try:
    import pandas as pd
    version = pd.__version__
    majorVersion = int(version[0])
    pd.set_option('display.max_colwidth', None)
    is_pd = majorVersion>=1
    is_pd = True
except:
    pass


def get_type(arr):
    # type: (np.ndarray) -> str
    return str(type(arr))


def get_shape(arr):
    # type: (np.ndarray) -> str
    return str(arr.shape[0])


def get_head(arr):
    # type: (np.ndarray) -> str
    return repr(_create_table(arr).head(1).to_html(notebook=True))


def get_column_types(arr):
    # type: (np.ndarray) -> str
    table = _create_table(arr)
    cols_types = [str(t) for t in table.dtypes] if is_pd else table.get_cols_types()

    return NP_ROWS_TYPE + TABLE_TYPE_NEXT_VALUE_SEPARATOR + \
        TABLE_TYPE_NEXT_VALUE_SEPARATOR.join(cols_types)


def get_data(arr, use_csv_serialization, start_index=None, end_index=None, format=None):
    # type: (Union[np.ndarray, dict], bool, Union[int, None], Union[int, None], Union[str, None]) -> str
    def convert_data_to_html(data, format):
        return repr(_create_table(data, start_index, end_index, format).to_html(notebook=True))

    def convert_data_to_csv(data, format):
        return repr(_create_table(data, start_index, end_index, format).to_csv(na_rep = "None", float_format=format, sep=CSV_FORMAT_SEPARATOR))

    if use_csv_serialization:
        computed_data = _compute_data(arr, convert_data_to_csv, format)
    else:
        computed_data = _compute_data(arr, convert_data_to_html, format)
    return computed_data


def display_data_html(arr, start_index=None, end_index=None):
    # type: (np.ndarray, int, int) -> None
    def ipython_display(data, format):
        from IPython.display import display, HTML
        display(HTML(_create_table(data, start_index, end_index).to_html(notebook=True)))

    _compute_data(arr, ipython_display)


def display_data_csv(arr, start_index=None, end_index=None):
    # type: (np.ndarray, int, int) -> None
    def ipython_display(data, format):
        print(_create_table(data, start_index, end_index).to_csv(na_rep = "None", sep=CSV_FORMAT_SEPARATOR, float_format=format))

    _compute_data(arr, ipython_display)


class _NpTable:
    def __init__(self, np_array, format=None):
        self.array = np_array
        self.type = self.get_array_type()
        self.indexes = None
        self.format = format

    def get_array_type(self):
        if len(self.array.shape) > 1:
            return TWO_DIM

        return ONE_DIM

    def get_cols_types(self):
        dtype = self.array.dtype
        if "torch" in str(dtype):
            col_type = dtype
        else:
            col_type = dtype.name

        if self.type == ONE_DIM:
            # [1, 2, 3] -> [int]
            return [str(col_type)]

        # [[1, 2], [3, 4]] -> [int, int]
        return [str(col_type) for _ in range(len(self.array[0]))]

    def head(self, num_rows):
        if self.array.shape[0] < 6:
            return self

        return _NpTable(self.array[:num_rows]).sort()

    def to_html(self, notebook):
        html = ['<table class="dataframe">\n']

        # columns names
        html.append('<thead>\n'
                    '<tr style="text-align: right;">\n'
                    '<th></th>\n')
        html += self._collect_cols_names_html()
        html.append('</tr>\n'
                    '</thead>\n')

        # tbody
        html += self._collect_values_html(None)

        html.append('</table>\n')

        return "".join(html)

    def _collect_cols_names_html(self):
        if self.type == ONE_DIM:
            return ['<th>0</th>\n']

        return ['<th>{}</th>\n'.format(i) for i in range(len(self.array[0]))]

    def _collect_values_html(self, max_cols):
        html = ['<tbody>\n']
        rows = self.array.shape[0]
        for row_num in range(rows):
            html.append('<tr>\n')
            html.append('<th>{}</th>\n'.format(int(self.indexes[row_num])))
            if self.type == ONE_DIM:
                # None usually is not supported in tensors, but to be totally sure
                if self.format is not None and self.array[row_num] is not None:
                    value = self.format % self.array[row_num]
                else:
                    value = self.array[row_num]
                html.append('<td>{}</td>\n'.format(value))
            else:
                cols = len(self.array[0])
                max_cols = cols if max_cols is None else min(max_cols, cols)
                for col_num in range(max_cols):
                    if self.format is not None and self.array[row_num][col_num]:
                        value = self.format % self.array[row_num][col_num]
                    else:
                        value = self.array[row_num][col_num]
                    html.append('<td>{}</td>\n'.format(value))
            html.append('</tr>\n')
        html.append('</tbody>\n')
        return html

    def to_csv(self, na_rep = "None", float_format=None, sep=CSV_FORMAT_SEPARATOR):
        csv_stream = io.StringIO()
        if float_format is None or float_format == 'null':
            float_format = "%s"

        np.savetxt(csv_stream, self.array, delimiter=CSV_FORMAT_SEPARATOR, fmt=float_format)
        csv_string = csv_stream.getvalue()
        csv_rows_with_index = self._insert_index_at_rows_begging_csv(csv_string)

        col_names = self._collect_col_names_csv()
        return col_names + "\n" + csv_rows_with_index

    def _insert_index_at_rows_begging_csv(self, csv_string):
        # type: (str) -> str
        csv_rows = csv_string.split('\n')
        csv_rows_with_index = []
        for row_index in range(self.array.shape[0]):
            csv_rows_with_index.append(str(row_index) + CSV_FORMAT_SEPARATOR + csv_rows[row_index])
        return "\n".join(csv_rows_with_index)

    def _collect_col_names_csv(self):
        if self.type == ONE_DIM:
            return '{}0'.format(CSV_FORMAT_SEPARATOR)

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
            return self._sort_by_index(True in orders)

        if self.type == ONE_DIM:
            extended = np.column_stack((self.indexes, self.array))
            sort_extended = extended[:, 1].argsort()
            if False in orders:
                sort_extended = sort_extended[::-1]
            result = extended[sort_extended]
            self.array = result[:, 1]
            self.indexes = result[:, 0]
            return self

        extended = np.insert(self.array, 0, self.indexes, axis=1)
        for i in range(len(cols) - 1, -1, -1):
            sort = extended[:, cols[i]].argsort(kind='stable')
            extended = extended[sort if orders[i] else sort[::-1]]
        self.indexes = extended[:, 0]
        self.array = extended[:, 1:]
        return self

    def _sort_by_index(self, order):
        if order:
            return self
        self.array = self.array[::-1]
        self.indexes = self.indexes[::-1]
        return self


def _sort_df(dataframe, sort_keys):
    if sort_keys is None:
        return dataframe

    cols, orders = sort_keys
    if 0 in cols:
        if len(cols) == 1:
            return dataframe.sort_index(ascending=orders[0])
        return dataframe.sort_index(level=cols, ascending=orders)
    sort_by = list(map(lambda c: dataframe.columns[c - 1], cols))
    return dataframe.sort_values(by=sort_by, ascending=orders)


def _create_table(command, start_index=None, end_index=None, format=None):
    sort_keys = None

    if type(command) is dict:
        np_array = command['data']
        sort_keys = command['sort_keys']
    else:
        np_array = command

    try:
        import tensorflow as tf
        if isinstance(np_array, tf.SparseTensor):
            np_array = tf.sparse.to_dense(tf.sparse.reorder(np_array))
    except ImportError:
        pass
    try:
        import torch
        if isinstance(np_array, torch.Tensor):
            np_array = np_array.to_dense()
    except ImportError:
        pass

    if is_pd:
        sorting_arr = _sort_df(pd.DataFrame(np_array), sort_keys)
        if start_index is not None and end_index is not None:
            return sorting_arr.iloc[start_index:end_index]
        return sorting_arr

    return _NpTable(np_array, format=format).sort(sort_keys).slice(start_index, end_index)


def _compute_data(arr, fun, format=None):
    is_sort_command = type(arr) is dict
    data = arr['data'] if is_sort_command else arr

    try:
        import tensorflow as tf
        if data.dtype == tf.bfloat16:
            data = tf.convert_to_tensor(data.numpy().astype(np.float32))
    except (ImportError, AttributeError):
        pass

    jb_max_cols, jb_max_colwidth, jb_max_rows, jb_float_options = None, None, None, None
    if is_pd:
        jb_max_cols, jb_max_colwidth, jb_max_rows, jb_float_options = _set_pd_options(format)

    if is_sort_command:
        arr['data'] = data
        data = arr

    data = fun(data, pd.get_option('display.float_format'))

    if is_pd:
        _reset_pd_options(jb_max_cols, jb_max_colwidth, jb_max_rows, jb_float_options)

    return data


def __get_tables_display_options():
    # type: () -> Tuple[None, Union[int, None], None]
    import sys
    if sys.version_info < (3, 0):
        return None, MAX_COLWIDTH, None
    try:
        import pandas as pd
        if int(pd.__version__.split('.')[0]) < 1:
            return None, MAX_COLWIDTH, None
    except Exception:
        pass
    return None, None, None


def _set_pd_options(format):
    max_cols, max_colwidth, max_rows = __get_tables_display_options()
    _jb_float_options = None

    _jb_max_cols = pd.get_option('display.max_columns')
    _jb_max_colwidth = pd.get_option('display.max_colwidth')
    _jb_max_rows = pd.get_option('display.max_rows')
    if format is not None:
        _jb_float_options = pd.get_option('display.float_format')
    format_function = _define_format_function(format)
    if format_function is not None:
        pd.set_option('display.float_format', format_function)

    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_rows', max_rows)
    try:
        pd.set_option('display.max_colwidth', max_colwidth)
    except ValueError:
        pd.set_option('display.max_colwidth', MAX_COLWIDTH_PYTHON_2)

    return _jb_max_cols, _jb_max_colwidth, _jb_max_rows, _jb_float_options


def _reset_pd_options(max_cols, max_colwidth, max_rows, float_format):
    pd.set_option('display.max_columns', max_cols)
    pd.set_option('display.max_colwidth', max_colwidth)
    pd.set_option('display.max_rows', max_rows)
    if float_format is not None:
        pd.set_option('display.float_format', float_format)


def _define_format_function(format):
    # type: (Union[None, str]) -> Union[Callable, None]
    if format is None or format == 'null':
        return None

    if format.startswith("%"):
        return lambda x: format % x
    else:
        return None
