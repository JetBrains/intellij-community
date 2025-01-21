"""Contains methods for building Thrift structures for interacting with IDE

The methods from this file are used for Python console interaction. Please
note that the debugger still uses XML structures with the similar methods
contained in `pydevd_xml.py` file.
"""
import sys
import traceback

from _pydev_bundle import pydev_log
from _pydevd_bundle import pydevd_extension_utils
from _pydevd_bundle import pydevd_resolver
from _pydevd_bundle.pydevd_constants import dict_iter_items, dict_keys, IS_PY3K, \
    MAXIMUM_VARIABLE_REPRESENTATION_SIZE, RETURN_VALUES_DICT, LOAD_VALUES_POLICY, DEFAULT_VALUES_DICT, NUMPY_NUMERIC_TYPES, \
    GET_FRAME_RETURN_GROUP
from _pydevd_bundle.pydevd_extension_api import TypeResolveProvider, StrPresentationProvider
from _pydevd_bundle.pydevd_user_type_renderers_utils import try_get_type_renderer_for_var
from _pydevd_bundle.pydevd_utils import  is_string, should_evaluate_full_value, should_evaluate_shape
from _pydevd_bundle.pydevd_vars import get_label, array_default_format, is_able_to_format_number, MAXIMUM_ARRAY_SIZE, \
    get_column_formatter_by_type, get_formatted_row_elements, IAtPolarsAccessor, DEFAULT_DF_FORMAT, DATAFRAME_HEADER_LOAD_MAX_SIZE
from pydev_console.pydev_protocol import DebugValue, GetArrayResponse, ArrayData, ArrayHeaders, ColHeader, RowHeader, \
    UnsupportedArrayTypeException, ExceedingArrayDimensionsException
from _pydevd_bundle.pydevd_xml import ExceptionOnEvaluate
from _pydevd_bundle.pydevd_frame_type_handler import get_vars_handler, DO_NOT_PROCESS_VARS, THRIFT_COMMUNICATION_VARS_HANDLER
from _pydevd_bundle.pydevd_repr_utils import get_value_repr

try:
    import types

    frame_type = types.FrameType
except:
    frame_type = None


_IS_JYTHON = sys.platform.startswith("java")


def _create_default_type_map():
    if not _IS_JYTHON:
        default_type_map = [
            # None means that it should not be treated as a compound variable

            # isintance does not accept a tuple on some versions of python, so, we must declare it expanded
            (type(None), None,),
            (int, None),
            (float, None),
            (complex, None),
            (str, None),
            (tuple, pydevd_resolver.tupleResolver),
            (list, pydevd_resolver.tupleResolver),
            (dict, pydevd_resolver.dictResolver),
        ]
        try:
            default_type_map.append((long, None))  # @UndefinedVariable
        except:
            pass  # not available on all python versions

        try:
            default_type_map.append((unicode, None))  # @UndefinedVariable
        except:
            pass  # not available on all python versions

        try:
            default_type_map.append((set, pydevd_resolver.setResolver))
        except:
            pass  # not available on all python versions

        try:
            default_type_map.append((frozenset, pydevd_resolver.setResolver))
        except:
            pass  # not available on all python versions

        try:
            from django.utils.datastructures import MultiValueDict
            default_type_map.insert(0, (MultiValueDict, pydevd_resolver.multiValueDictResolver))
            # we should put it before dict
        except:
            pass  # django may not be installed

        try:
            from django.forms import BaseForm
            default_type_map.insert(0, (BaseForm, pydevd_resolver.djangoFormResolver))
            # we should put it before instance resolver
        except:
            pass  # django may not be installed

        try:
            from collections import deque
            default_type_map.append((deque, pydevd_resolver.dequeResolver))
        except:
            pass

        if frame_type is not None:
            default_type_map.append((frame_type, pydevd_resolver.frameResolver))

    else:
        from org.python import core  # @UnresolvedImport
        default_type_map = [
            (core.PyNone, None),
            (core.PyInteger, None),
            (core.PyLong, None),
            (core.PyFloat, None),
            (core.PyComplex, None),
            (core.PyString, None),
            (core.PyTuple, pydevd_resolver.tupleResolver),
            (core.PyList, pydevd_resolver.tupleResolver),
            (core.PyDictionary, pydevd_resolver.dictResolver),
            (core.PyStringMap, pydevd_resolver.dictResolver),
        ]
        if hasattr(core, 'PyJavaInstance'):
            # Jython 2.5b3 removed it.
            default_type_map.append((core.PyJavaInstance, pydevd_resolver.instanceResolver))

    return default_type_map


class TypeResolveHandler(object):
    NO_PROVIDER = []  # Sentinel value (any mutable object to be used as a constant would be valid).

    def __init__(self):
        # Note: don't initialize with the types we already know about so that the extensions can override
        # the default resolvers that are already available if they want.
        self._type_to_resolver_cache = {}
        self._type_to_str_provider_cache = {}
        self._initialized = False

    def _initialize(self):
        self._default_type_map = _create_default_type_map()
        self._resolve_providers = pydevd_extension_utils.extensions_of_type(TypeResolveProvider)
        self._str_providers = pydevd_extension_utils.extensions_of_type(StrPresentationProvider)
        self._initialized = True

    def get_type(self, o):
        try:
            try:
                # Faster than type(o) as we don't need the function call.
                type_object = o.__class__
            except:
                # Not all objects have __class__ (i.e.: there are bad bindings around).
                type_object = type(o)

            type_name = type_object.__name__
        except:
            # This happens for org.python.core.InitModule
            return 'Unable to get Type', 'Unable to get Type', None

        return self._get_type(o, type_object, type_name)

    def _get_type(self, o, type_object, type_name):
        resolver = self._type_to_resolver_cache.get(type_object)
        if resolver is not None:
            return type_object, type_name, resolver

        if not self._initialized:
            self._initialize()

        try:
            for resolver in self._resolve_providers:
                if resolver.can_provide(type_object, type_name):
                    # Cache it
                    self._type_to_resolver_cache[type_object] = resolver
                    return type_object, type_name, resolver

            for t in self._default_type_map:
                if isinstance(o, t[0]):
                    # Cache it
                    resolver = t[1]
                    self._type_to_resolver_cache[type_object] = resolver
                    return (type_object, type_name, resolver)
        except:
            traceback.print_exc()

        # No match return default (and cache it).
        resolver = pydevd_resolver.defaultResolver
        self._type_to_resolver_cache[type_object] = resolver
        return type_object, type_name, resolver

    if _IS_JYTHON:
        _base_get_type = _get_type

        def _get_type(self, o, type_object, type_name):
            if type_name == 'org.python.core.PyJavaInstance':
                return type_object, type_name, pydevd_resolver.instanceResolver

            if type_name == 'org.python.core.PyArray':
                return type_object, type_name, pydevd_resolver.jyArrayResolver

            return self._base_get_type(o, type_name, type_name)

    def str_from_providers(self, o, type_object, type_name, do_trim=True):
        provider = self._type_to_str_provider_cache.get(type_object)

        if provider is self.NO_PROVIDER:
            return None

        if provider is not None:
            try:
                return provider.get_str(o, do_trim)
            except TypeError:
                return provider.get_str(o)

        if not self._initialized:
            self._initialize()

        for provider in self._str_providers:
            if provider.can_provide(type_object, type_name):
                self._type_to_str_provider_cache[type_object] = provider
                try:
                    return provider.get_str(o, do_trim)
                except TypeError:
                    return provider.get_str(o)

        self._type_to_str_provider_cache[type_object] = self.NO_PROVIDER
        return None


_TYPE_RESOLVE_HANDLER = TypeResolveHandler()

""" 
def get_type(o):
    Receives object and returns a triple (typeObject, typeString, resolver).

    resolver != None means that variable is a container, and should be displayed as a hierarchy.

    Use the resolver to get its attributes.

    All container objects should have a resolver.
"""
get_type = _TYPE_RESOLVE_HANDLER.get_type

_str_from_providers = _TYPE_RESOLVE_HANDLER.str_from_providers

def get_sorted_keys(frame_f_locals):
    keys = dict_keys(frame_f_locals)
    if hasattr(keys, 'sort'):
        keys.sort()  # Python 3.0 does not have it
    else:
        keys = sorted(keys)  # Jython 2.1 does not have it
    return keys


def frame_vars_to_struct(frame_f_locals, group_type, hidden_ns=None, user_type_renderers={}):
    """Returns frame variables as the list of `DebugValue` structures
    """
    keys = get_sorted_keys(frame_f_locals)

    type_handler = get_vars_handler(var_to_struct,
                                    handler_type=THRIFT_COMMUNICATION_VARS_HANDLER,
                                    group_type=group_type)

    for k in keys:
        try:
            v = frame_f_locals[k]
            eval_full_val = should_evaluate_full_value(v, group_type)

            type_handler.handle(k, v, hidden_ns, eval_full_val, user_type_renderers=user_type_renderers)
        except Exception:
            traceback.print_exc()
            pydev_log.error("Unexpected error, recovered safely.\n")

    # Show return values as the first entry.
    return type_handler.get_list()


def _get_default_var_string_representation(v, _type, typeName, format, do_trim=True):
    str_from_provider = _str_from_providers(v, _type, typeName, do_trim)
    if str_from_provider is not None:
        return str_from_provider

    return get_value_repr(v, do_trim, format)


def var_to_struct(val, name, format='%s', do_trim=True, evaluate_full_value=True, user_type_renderers=None):
    """ single variable or dictionary to Thrift struct representation """

    debug_value = DebugValue()

    if name in DO_NOT_PROCESS_VARS:
        debug_value.name = name
        debug_value.value = val
        return debug_value

    try:
        # This should be faster than isinstance (but we have to protect against not having a '__class__' attribute).
        is_exception_on_eval = val.__class__ == ExceptionOnEvaluate
    except:
        is_exception_on_eval = False

    if is_exception_on_eval:
        v = val.result
    else:
        v = val

    _type, typeName, resolver = get_type(v)

    # type qualifier to struct
    type_qualifier = getattr(_type, "__module__", "")
    if type_qualifier:
        debug_value.qualifier = type_qualifier

    # type renderer to struct
    type_renderer = None
    if user_type_renderers is not None:
        type_renderer = try_get_type_renderer_for_var(v, user_type_renderers)
    if type_renderer is not None:
        debug_value.typeRendererId = type_renderer.to_type

    # name and type to struct
    debug_value.name = name
    debug_value.type = typeName

    # value to struct
    value = None
    if not evaluate_full_value:
        value = DEFAULT_VALUES_DICT[LOAD_VALUES_POLICY]
    elif type_renderer is not None:
        value = type_renderer.evaluate_var_string_repr(v)
    if value is None:
        value = _get_default_var_string_representation(v, _type, typeName, format, do_trim)

    # fix to work with unicode values
    try:
        if not IS_PY3K:
            if value.__class__ == unicode:  # @UndefinedVariable
                value = value.encode('utf-8')
        else:
            if value.__class__ == bytes:
                value = value.encode('utf-8')
    except TypeError:  # in java, unicode is a function
        pass

    debug_value.value = value

    # shape to struct
    try:
        if should_evaluate_shape():
            if hasattr(v, 'shape') and not callable(v.shape):
                debug_value.shape = str(tuple(v.shape))
            elif hasattr(v, '__len__') and not is_string(v):
                debug_value.shape = str(len(v))
    except:
        pass

    # additional info to struct
    if is_exception_on_eval:
        debug_value.isErrorOnEval = True
    else:
        if resolver is not None:
            debug_value.isContainer = True
        else:
            pass

    return debug_value


def var_to_str(val, format, do_trim=True, evaluate_full_value=True):
    struct = var_to_struct(val, '', format, do_trim, evaluate_full_value)
    value = struct.value
    return value if value is not None else ''


# from pydevd_vars.py

def array_to_thrift_struct(array, name, roffset, coffset, rows, cols, format):
    """
    """

    array, array_chunk, r, c, f = array_to_meta_thrift_struct(array, name, format)
    format = '%' + f
    if rows == -1 and cols == -1:
        rows = r
        cols = c

    rows = min(rows, MAXIMUM_ARRAY_SIZE)
    cols = min(cols, MAXIMUM_ARRAY_SIZE)

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

    array_chunk.data = array_data_to_thrift_struct(rows, cols, lambda r: (get_value(r, c) for c in range(cols)), format)
    return array_chunk


def tensor_to_thrift_struct(tensor, name, roffset, coffset, rows, cols, format):
    try:
        return array_to_thrift_struct(tensor.numpy(), name, roffset, coffset, rows, cols, format)
    except TypeError:
        return array_to_thrift_struct(tensor.to_dense().numpy(), name, roffset, coffset, rows, cols, format)


def sparse_tensor_to_thrift_struct(tensor, name, roffset, coffset, rows, cols, format):
    try:
        import tensorflow as tf
        return tensor_to_thrift_struct(tf.sparse.to_dense(tf.sparse.reorder(tensor)), name, roffset, coffset, rows, cols, format)
    except ImportError:
        pass


def dataset_to_thrift_struct(dataset, name, roffset, coffset, rows, cols, format):
    return dataframe_to_thrift_struct(dataset.to_pandas(), name, roffset, coffset, rows, cols, format)


def array_to_meta_thrift_struct(array, name, format):
    type = array.dtype.kind
    slice = name
    l = len(array.shape)

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
        raise ExceedingArrayDimensionsException
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
    array_chunk = GetArrayResponse()
    array_chunk.slice = slice
    array_chunk.rows = rows
    array_chunk.cols = cols
    array_chunk.format = "%" + format
    array_chunk.type = type
    array_chunk.max = "%s" % bounds[1]
    array_chunk.min = "%s" % bounds[0]
    return array, array_chunk, rows, cols, format


def dataframe_to_thrift_struct(df, name, roffset, coffset, rows, cols, format):
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
    array_chunk = GetArrayResponse()
    array_chunk.slice = name
    array_chunk.rows = num_rows
    array_chunk.cols = num_cols
    array_chunk.type = ""
    array_chunk.max = "0"
    array_chunk.min = "0"
    format = format.replace("%", "")
    if not format:
        if num_rows > 0 and num_cols == 1:  # series or data frame with one column
            try:
                kind = df.dtype.kind
            except AttributeError:
                try:
                    kind = df.dtypes[0].kind
                except (IndexError, KeyError, AttributeError):
                    kind = "O"
            format = array_default_format(kind)
        else:
            format = array_default_format(DEFAULT_DF_FORMAT)
    array_chunk.format = "%" + format

    if (rows, cols) == (-1, -1):
        rows, cols = num_rows, num_cols

    elif (rows, cols) == (0, 0):
        # return header only
        r = min(num_rows, DATAFRAME_HEADER_LOAD_MAX_SIZE)
        c = min(num_cols, DATAFRAME_HEADER_LOAD_MAX_SIZE)
        array_chunk.headers = header_data_to_thrift_struct(r, c, [""] * num_cols, [(0, 0)] * num_cols, lambda x: DEFAULT_DF_FORMAT, original_df, dim)

        array_chunk.data = array_data_to_thrift_struct(rows, cols, None, '%' + format)
        return array_chunk

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

    def col_to_format(c):
        return get_column_formatter_by_type(format, dtypes[c])

    if dim == -1:
        iat = IAtPolarsAccessor(df)
    elif dim == 1 or len(df.columns.unique()) == len(df.columns):
        iat = df.iat
    else:
        iat = df.iloc

    def formatted_row_elements(row):
        return get_formatted_row_elements(row, iat, dim, cols, format, dtypes)

    array_chunk.headers = header_data_to_thrift_struct(rows, cols, dtypes, col_bounds, col_to_format, df, dim)
    # we already have here formatted_row_elements, so we pass here %s as a default format
    array_chunk.data = array_data_to_thrift_struct(rows, cols, formatted_row_elements, format='%s')
    return array_chunk


def array_data_to_thrift_struct(rows, cols, get_row, format):
    array_data = ArrayData()
    array_data.rows = rows
    array_data.cols = cols
    # `ArrayData.data`
    data = []
    for row in range(rows):
        data.append([var_to_str(value, format) for value in get_row(row)])

    array_data.data = data
    return array_data


def header_data_to_thrift_struct(rows, cols, dtypes, col_bounds, col_to_format, df, dim):
    array_headers = ArrayHeaders()
    col_headers = []
    for col in range(cols):
        col_label = get_label(df.axes[1].values[col]) if dim > 1 else str(col)
        bounds = col_bounds[col]
        col_format = "%" + col_to_format(col)
        col_header = ColHeader()
        # col_header.index = col
        col_header.label = col_label
        col_header.type = dtypes[col]
        col_header.format = col_to_format(col)
        col_header.max = col_format % bounds[1]
        col_header.min = col_format % bounds[0]
        col_headers.append(col_header)
    row_headers = []
    for row in range(rows):
        row_header = RowHeader()
        row_header.index = row
        row_header.label = get_label(df.axes[0].values[row] if dim != -1 else str(row))
        row_headers.append(row_header)
    array_headers.colHeaders = col_headers
    array_headers.rowHeaders = row_headers
    return array_headers


TYPE_TO_THRIFT_STRUCT_CONVERTERS = {
    "ndarray": array_to_thrift_struct,
    "EagerTensor": tensor_to_thrift_struct,
    "ResourceVariable": tensor_to_thrift_struct,
    "SparseTensor": sparse_tensor_to_thrift_struct,
    "Tensor": tensor_to_thrift_struct,
    "DataFrame": dataframe_to_thrift_struct,
    "Series": dataframe_to_thrift_struct,
    "Dataset": dataset_to_thrift_struct,
    "GeoDataFrame": dataframe_to_thrift_struct,
    "GeoSeries": dataframe_to_thrift_struct
}


def table_like_struct_to_thrift_struct(array, name, roffset, coffset, rows, cols, format):
    """Returns `GetArrayResponse` structure for table-like structure

    The `array` might be either `numpy.ndarray`, `pandas.DataFrame` or `pandas.Series`.
    """
    _, type_name, _ = get_type(array)
    format = format if is_able_to_format_number(format) else '%'
    if type_name in TYPE_TO_THRIFT_STRUCT_CONVERTERS:
        return TYPE_TO_THRIFT_STRUCT_CONVERTERS[type_name](array, name, roffset, coffset, rows, cols, format)
    else:
        raise UnsupportedArrayTypeException(type_name)
