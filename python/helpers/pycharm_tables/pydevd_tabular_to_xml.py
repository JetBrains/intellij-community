"""
Legacy code that is needed at the moment, during the transition from pydevd to debugpy.
"""
from _pydev_bundle.pydev_imports import quote
from _pydevd_bundle.pydevd_xml import get_type, var_to_xml

try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO

try:
    from collections import OrderedDict
except:
    OrderedDict = dict

DEFAULT_DF_FORMAT = "s"
MAXIMUM_ARRAY_SIZE = float('inf')
DATAFRAME_HEADER_LOAD_MAX_SIZE = 100
NUMPY_NUMERIC_TYPES = "biufc"
NUMPY_FLOATING_POINT_TYPES = "fc"

class VariableError(RuntimeError): pass
class ExceedingArrayDimensionsException(Exception): pass
class IAtPolarsAccessor:
    def __init__(self, ps):
        self.ps = ps

    def __getitem__(self, row):
        return self.ps[row]


def __is_able_to_format_number(format):
    try:
        import math
        format % math.pi
    except Exception:
        return False
    return True


def __is_complex(attr):
    complex_indicators = ['[', ']', '.']
    for indicator in complex_indicators:
        if attr.find(indicator) != -1:
            return True
    return False


def __array_default_format(type):
    if type == 'f':
        return '.5f'
    elif type == 'i' or type == 'u':
        return 'd'
    else:
        return 's'


def __get_formatted_row_elements(row, iat, dim, cols, format, dtypes):
    for c in range(cols):
        val = iat[row, c] if dim > 1 else iat[row]
        col_formatter = __get_column_formatter_by_type(format, dtypes[c])
        try:
            if val != val:
                yield "nan"
            else:
                yield ("%" + col_formatter) % (val,)
        except TypeError:
            yield ("%" + DEFAULT_DF_FORMAT) % (val,)


def __get_column_formatter_by_type(initial_format, column_type):
    if column_type in NUMPY_NUMERIC_TYPES and initial_format:
        if column_type in NUMPY_FLOATING_POINT_TYPES and initial_format.strip() == DEFAULT_DF_FORMAT:
            # use custom formatting for floats when default formatting is set
            return __array_default_format(column_type)
        return initial_format
    else:
        return __array_default_format(column_type)


def __get_label(label):
    return str(label) if not isinstance(label, tuple) else '/'.join(map(str, label))


def __array_to_meta_xml(array, name, format):
    type = array.dtype.kind
    slice = name
    l = len(array.shape)

    if l == 0:
        rows, cols = 0, 0
        bounds = (0, 0)
        return array, __slice_to_xml(name, rows, cols, format, "", bounds), rows, cols, format

    try:
        import numpy as np
        if isinstance(array, np.recarray) and l > 1:
            slice = "{}['{}']".format(slice, array.dtype.names[0])
            array = array[array.dtype.names[0]]
    except ImportError:
        pass

    # initial load, compute slice
    if format == '%':
        if l > 2:
            slice += '[0]' * (l - 2)
            for r in range(l - 2):
                array = array[0]
        if type == 'f':
            format = '.5f'
        elif type == 'i' or type == 'u':
            format = 'd'
        else:
            format = 's'
    else:
        format = format.replace('%', '')

    l = len(array.shape)
    reslice = ""
    if l > 2:
        raise ExceedingArrayDimensionsException()
    elif l == 1:
        # special case with 1D arrays arr[i, :] - row, but arr[:, i] - column with equal shape and ndim
        # http://stackoverflow.com/questions/16837946/numpy-a-2-rows-1-column-file-loadtxt-returns-1row-2-columns
        # explanation: http://stackoverflow.com/questions/15165170/how-do-i-maintain-row-column-orientation-of-vectors-in-numpy?rq=1
        # we use kind of a hack - get information about memory from C_CONTIGUOUS
        cols = 1
        rows = len(array)
        if rows < len(array):
            reslice = '[0:%s]' % (rows)
        array = array[0:rows]
    elif l == 2:
        rows = array.shape[-2]
        cols = array.shape[-1]
        if cols < array.shape[-1] or rows < array.shape[-2]:
            reslice = '[0:%s, 0:%s]' % (rows, cols)
        array = array[0:rows, 0:cols]

    # avoid slice duplication
    if not slice.endswith(reslice):
        slice += reslice

    bounds = (0, 0)
    if type in NUMPY_NUMERIC_TYPES and array.size != 0:
        bounds = (array.min(), array.max())
    return array, __slice_to_xml(slice, rows, cols, format, type, bounds), rows, cols, format


def __array_data_to_xml(rows, cols, get_row, format):
    xml = "<arraydata rows=\"%s\" cols=\"%s\"/>\n" % (rows, cols)
    for row in range(rows):
        xml += "<row index=\"%s\"/>\n" % row
        for value in get_row(row):
            xml += var_to_xml(value, '', format=format)
    return xml


def __slice_to_xml(slice, rows, cols, format, type, bounds):
    return '<array slice=\"%s\" rows=\"%s\" cols=\"%s\" format=\"%s\" type=\"%s\" max=\"%s\" min=\"%s\"/>' % \
        (quote(slice), rows, cols, quote(format), type, quote(str(bounds[1])), quote(str(bounds[0])))



def __header_data_to_xml(rows, cols, dtypes, col_bounds, col_to_format, df, dim):
    xml = "<headerdata rows=\"%s\" cols=\"%s\">\n" % (rows, cols)
    for col in range(cols):
        col_label = quote(__get_label(df.axes[1][col]) if dim > 1 else str(col))
        bounds = col_bounds[col]
        col_format = "%" + col_to_format(dtypes[col])
        xml += '<colheader index=\"%s\" label=\"%s\" type=\"%s\" format=\"%s\" max=\"%s\" min=\"%s\" />\n' % \
               (str(col), col_label, dtypes[col], col_to_format(dtypes[col]), quote(str(col_format % bounds[1])), quote(str(col_format % bounds[0])))
    for row in range(rows):
        xml += "<rowheader index=\"%s\" label = \"%s\"/>\n" % (str(row), quote(__get_label(df.axes[0][row] if dim != -1 else str(row))))
    xml += "</headerdata>\n"
    return xml


def legacy_array_to_xml(array, name, roffset, coffset, rows, cols, format):
    array, xml, r, c, f = __array_to_meta_xml(array, name, format)
    format = '%' + f
    if rows == -1 and cols == -1:
        rows = r
        cols = c

    rows = min(rows, MAXIMUM_ARRAY_SIZE)
    cols = min(cols, MAXIMUM_ARRAY_SIZE)

    if rows == 0 and cols == 0:
        return xml

    # there is no obvious rule for slicing (at least 5 choices)
    if len(array) == 1 and (rows > 1 or cols > 1):
        array = array[0]
    if array.size > len(array):
        array = array[roffset:, coffset:]
        rows = min(rows, len(array))
        cols = min(cols, len(array[0]))
        if len(array) == 1:
            array = array[0]
    elif array.size == len(array):
        if roffset == 0 and rows == 1:
            array = array[coffset:]
            cols = min(cols, len(array))
        elif coffset == 0 and cols == 1:
            array = array[roffset:]
            rows = min(rows, len(array))

    def get_value(row, col):
        value = array
        if rows == 1 or cols == 1:
            if rows == 1 and cols == 1:
                value = array[0]
            else:
                value = array[(col if rows == 1 else row)]
                if "ndarray" in str(type(value)):
                    value = value[0]
        else:
            value = array[row][col]
        return value
    xml += __array_data_to_xml(rows, cols, lambda r: (get_value(r, c) for c in range(cols)), format)
    return xml


def legacy_dataframe_to_xml(df, name, roffset, coffset, rows, cols, format):
    """
    :type df: pandas.core.frame.DataFrame
    :type name: str
    :type coffset: int
    :type roffset: int
    :type rows: int
    :type cols: int
    :type format: str
    """
    original_df = df
    dim = len(df.axes) if hasattr(df, 'axes') else -1
    num_rows = df.shape[0]
    num_cols = df.shape[1] if dim > 1 else 1
    format = format.replace('%', '')

    if not format:
        if num_rows > 0 and num_cols == 1:  # series or data frame with one column
            try:
                kind = df.dtype.kind
            except AttributeError:
                try:
                    kind = df.dtypes.iloc[0].kind
                except (IndexError, KeyError, AttributeError):
                    kind = 'O'
            format = __array_default_format(kind)
        else:
            format = __array_default_format(DEFAULT_DF_FORMAT)

    xml = __slice_to_xml(name, num_rows, num_cols, format, "", (0, 0))

    if (rows, cols) == (-1, -1):
        rows, cols = num_rows, num_cols

    elif (rows, cols) == (0, 0):
        # return header only
        r = min(num_rows, DATAFRAME_HEADER_LOAD_MAX_SIZE)
        c = min(num_cols, DATAFRAME_HEADER_LOAD_MAX_SIZE)
        xml += __header_data_to_xml(r, c, [""] * num_cols, [(0, 0)] * num_cols, lambda x: DEFAULT_DF_FORMAT, original_df, dim)
        return xml

    rows = min(rows, MAXIMUM_ARRAY_SIZE)
    cols = min(cols, MAXIMUM_ARRAY_SIZE, num_cols)
    # need to precompute column bounds here before slicing!
    col_bounds = [None] * cols
    dtypes = [None] * cols
    if dim > 1:
        for col in range(cols):
            dtype = df.dtypes.iloc[coffset + col].kind
            dtypes[col] = dtype
            if dtype in NUMPY_NUMERIC_TYPES and df.size != 0:
                cvalues = df.iloc[:, coffset + col]
                bounds = (cvalues.min(), cvalues.max())
            else:
                bounds = (0, 0)
            col_bounds[col] = bounds
    elif dim == -1:
        dtype = '0'
        dtypes[0] = dtype
        col_bounds[0] = (df.min(), df.max()) if dtype in NUMPY_NUMERIC_TYPES and df.size != 0 else (0, 0)
    else:
        dtype = df.dtype.kind
        dtypes[0] = dtype
        col_bounds[0] = (df.min(), df.max()) if dtype in NUMPY_NUMERIC_TYPES and df.size != 0 else (0, 0)

    if dim > 1:
        df = df.iloc[roffset: roffset + rows, coffset: coffset + cols]
    elif dim == -1:
        df = df[roffset: roffset + rows]
    else:
        df = df.iloc[roffset: roffset + rows]

    rows = df.shape[0]
    cols = df.shape[1] if dim > 1 else 1

    def col_to_format(column_type):
        return __get_column_formatter_by_type(format, column_type)

    if dim == -1:
        iat = IAtPolarsAccessor(df)
    elif dim == 1 or len(df.columns.unique()) == len(df.columns):
        iat = df.iat
    else:
        iat = df.iloc

    def formatted_row_elements(row):
        return __get_formatted_row_elements(row, iat, dim, cols, format, dtypes)

    xml += __header_data_to_xml(rows, cols, dtypes, col_bounds, col_to_format, df, dim)

    # we already have here formatted_row_elements, so we pass here %s as a default format
    xml += __array_data_to_xml(rows, cols, formatted_row_elements, format='%s')
    return xml


def legacy_tf_to_xml(tensor, name, roffset, coffset, rows, cols, format):
    try:
        return legacy_array_to_xml(tensor.numpy(), name, roffset, coffset, rows, cols, format)
    except TypeError:
        return legacy_array_to_xml(tensor.to_dense().numpy(), name, roffset, coffset, rows, cols, format)


def legacy_tf_sparse_to_xml(tensor, name, roffset, coffset, rows, cols, format):
    try:
        import tensorflow as tf
        return legacy_tf_to_xml(tf.sparse.to_dense(tf.sparse.reorder(tensor)), name, roffset, coffset, rows, cols, format)
    except ImportError:
        pass


def legacy_torch_to_xml(tensor, name, roffset, coffset, rows, cols, format):
    try:
        if tensor.requires_grad:
            tensor = tensor.detach()
        return legacy_array_to_xml(tensor.numpy(), name, roffset, coffset, rows, cols, format)
    except TypeError:
        return legacy_array_to_xml(tensor.to_dense().numpy(), name, roffset, coffset, rows, cols, format)


def legacy_dataset_to_xml(dataset, name, roffset, coffset, rows, cols, format):
    return legacy_dataframe_to_xml(dataset.to_pandas(), name, roffset, coffset, rows, cols, format)


LEGACY_TYPE_TO_XML_CONVERTERS = {
    "ndarray": legacy_array_to_xml,
    "recarray": legacy_array_to_xml,
    "DataFrame": legacy_dataframe_to_xml,
    "Series": legacy_dataframe_to_xml,
    "GeoDataFrame": legacy_dataframe_to_xml,
    "GeoSeries": legacy_dataframe_to_xml,
    "EagerTensor": legacy_tf_to_xml,
    "ResourceVariable": legacy_tf_to_xml,
    "SparseTensor": legacy_tf_sparse_to_xml,
    "Tensor": legacy_torch_to_xml,
    "Dataset": legacy_dataset_to_xml
}


def legacy_table_like_struct_to_xml(array, name, roffset, coffset, rows, cols, format):
    _, type_name, _ = get_type(array)
    format = format if __is_able_to_format_number(format) else '%'
    if type_name in LEGACY_TYPE_TO_XML_CONVERTERS:
        return "<xml>%s</xml>" % LEGACY_TYPE_TO_XML_CONVERTERS[type_name](array, name, roffset, coffset, rows, cols, format)
    else:
        raise VariableError("type %s not supported" % type_name)