""" pydevd_vars deals with variables:
    resolution/conversion to XML.
"""
import math
import pickle

from _pydev_imps._pydev_saved_modules import thread
from _pydev_bundle.pydev_imports import quote
from _pydevd_bundle.pydevd_constants import get_frame, get_current_thread_id, xrange, NUMPY_NUMERIC_TYPES
from _pydevd_bundle.pydevd_custom_frames import get_custom_frame
from _pydevd_bundle.pydevd_xml import ExceptionOnEvaluate, get_type, var_to_xml

try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO
import sys  # @Reimport

try:
    from collections import OrderedDict
except:
    OrderedDict = dict

from _pydev_imps._pydev_saved_modules import threading
import traceback
from _pydevd_bundle import pydevd_save_locals
from _pydev_bundle.pydev_imports import Exec, execfile
from _pydevd_bundle.pydevd_utils import to_string, VariableWithOffset

SENTINEL_VALUE = []

# ------------------------------------------------------------------------------------------------------ class for errors

class VariableError(RuntimeError): pass


class FrameNotFoundError(RuntimeError): pass


def _iter_frames(initialFrame):
    '''NO-YIELD VERSION: Iterates through all the frames starting at the specified frame (which will be the first returned item)'''
    # cannot use yield
    frames = []

    while initialFrame is not None:
        frames.append(initialFrame)
        initialFrame = initialFrame.f_back

    return frames


def dump_frames(thread_id):
    sys.stdout.write('dumping frames\n')
    if thread_id != get_current_thread_id(threading.currentThread()):
        raise VariableError("find_frame: must execute on same thread")

    curFrame = get_frame()
    for frame in _iter_frames(curFrame):
        sys.stdout.write('%s\n' % pickle.dumps(frame))


# ===============================================================================
# AdditionalFramesContainer
# ===============================================================================
class AdditionalFramesContainer:
    lock = thread.allocate_lock()
    additional_frames = {}  # dict of dicts


def add_additional_frame_by_id(thread_id, frames_by_id):
    AdditionalFramesContainer.additional_frames[thread_id] = frames_by_id


addAdditionalFrameById = add_additional_frame_by_id  # Backward compatibility


def remove_additional_frame_by_id(thread_id):
    del AdditionalFramesContainer.additional_frames[thread_id]


removeAdditionalFrameById = remove_additional_frame_by_id  # Backward compatibility


def has_additional_frames_by_id(thread_id):
    return thread_id in AdditionalFramesContainer.additional_frames


def get_additional_frames_by_id(thread_id):
    return AdditionalFramesContainer.additional_frames.get(thread_id)


def find_frame(thread_id, frame_id):
    """ returns a frame on the thread that has a given frame_id """
    try:
        curr_thread_id = get_current_thread_id(threading.currentThread())
        if thread_id != curr_thread_id:
            try:
                return get_custom_frame(thread_id, frame_id)  # I.e.: thread_id could be a stackless frame id + thread_id.
            except:
                pass

            raise VariableError("find_frame: must execute on same thread (%s != %s)" % (thread_id, curr_thread_id))

        lookingFor = int(frame_id)

        if AdditionalFramesContainer.additional_frames:
            if thread_id in AdditionalFramesContainer.additional_frames:
                frame = AdditionalFramesContainer.additional_frames[thread_id].get(lookingFor)

                if frame is not None:
                    return frame

        curFrame = get_frame()
        if frame_id == "*":
            return curFrame  # any frame is specified with "*"

        frameFound = None

        for frame in _iter_frames(curFrame):
            if lookingFor == id(frame):
                frameFound = frame
                del frame
                break

            del frame

        # Important: python can hold a reference to the frame from the current context
        # if an exception is raised, so, if we don't explicitly add those deletes
        # we might have those variables living much more than we'd want to.

        # I.e.: sys.exc_info holding reference to frame that raises exception (so, other places
        # need to call sys.exc_clear())
        del curFrame

        if frameFound is None:
            msgFrames = ''
            i = 0

            for frame in _iter_frames(get_frame()):
                i += 1
                msgFrames += str(id(frame))
                if i % 5 == 0:
                    msgFrames += '\n'
                else:
                    msgFrames += '  -  '

# Note: commented this error message out (it may commonly happen 
# if a message asking for a frame is issued while a thread is paused
# but the thread starts running before the message is actually 
# handled).
# Leaving code to uncomment during tests.  
#             err_msg = '''find_frame: frame not found.
#     Looking for thread_id:%s, frame_id:%s
#     Current     thread_id:%s, available frames:
#     %s\n
#     ''' % (thread_id, lookingFor, curr_thread_id, msgFrames)
# 
#             sys.stderr.write(err_msg)
            return None

        return frameFound
    except:
        import traceback
        traceback.print_exc()
        return None


def getVariable(thread_id, frame_id, scope, attrs):
    """
    returns the value of a variable

    :scope: can be BY_ID, EXPRESSION, GLOBAL, LOCAL, FRAME

    BY_ID means we'll traverse the list of all objects alive to get the object.

    :attrs: after reaching the proper scope, we have to get the attributes until we find
            the proper location (i.e.: obj\tattr1\tattr2).

    :note: when BY_ID is used, the frame_id is considered the id of the object to find and
           not the frame (as we don't care about the frame in this case).
    """
    if scope == 'BY_ID':
        if thread_id != get_current_thread_id(threading.currentThread()):
            raise VariableError("getVariable: must execute on same thread")

        try:
            import gc
            objects = gc.get_objects()
        except:
            pass  # Not all python variants have it.
        else:
            frame_id = int(frame_id)
            for var in objects:
                if id(var) == frame_id:
                    if attrs is not None:
                        attrList = attrs.split('\t')
                        for k in attrList:
                            _type, _typeName, resolver = get_type(var)
                            var = resolver.resolve(var, k)

                    return var

        # If it didn't return previously, we coudn't find it by id (i.e.: alrceady garbage collected).
        sys.stderr.write('Unable to find object with id: %s\n' % (frame_id,))
        return None

    frame = find_frame(thread_id, frame_id)
    if frame is None:
        return {}

    if attrs is not None:
        attrList = attrs.split('\t')
    else:
        attrList = []

    for attr in attrList:
        attr.replace("@_@TAB_CHAR@_@", '\t')

    if scope == 'EXPRESSION':
        for count in xrange(len(attrList)):
            if count == 0:
                # An Expression can be in any scope (globals/locals), therefore it needs to evaluated as an expression
                var = evaluate_expression(thread_id, frame_id, attrList[count], False)
            else:
                _type, _typeName, resolver = get_type(var)
                var = resolver.resolve(var, attrList[count])
    else:
        if scope == "GLOBAL":
            var = frame.f_globals
            del attrList[0]  # globals are special, and they get a single dummy unused attribute
        else:
            # in a frame access both locals and globals as Python does
            var = {}
            var.update(frame.f_globals)
            var.update(frame.f_locals)

        for k in attrList:
            _type, _typeName, resolver = get_type(var)
            var = resolver.resolve(var, k)

    return var


def get_offset(attrs):
    """
    Extract offset from the given attributes.

    :param attrs: The string of a compound variable fields split by tabs.
      If an offset is given, it must go the first element.
    :return: The value of offset if given or 0.
    """
    offset = 0
    if attrs is not None:
        try:
            offset = int(attrs.split('\t')[0])
        except ValueError:
            pass
    return offset


def resolve_compound_variable_fields(thread_id, frame_id, scope, attrs):
    """
    Resolve compound variable in debugger scopes by its name and attributes

    :param thread_id: id of the variable's thread
    :param frame_id: id of the variable's frame
    :param scope: can be BY_ID, EXPRESSION, GLOBAL, LOCAL, FRAME
    :param attrs: after reaching the proper scope, we have to get the attributes until we find
            the proper location (i.e.: obj\tattr1\tattr2)
    :return: a dictionary of variables's fields

    :note: PyCharm supports progressive loading of large collections and uses the `attrs`
           parameter to pass the offset, e.g. 300\t\\obj\tattr1\tattr2 should return
           the value of attr2 starting from the 300th element. This hack makes it possible
           to add the support of progressive loading without extending of the protocol.
    """
    offset = get_offset(attrs)

    orig_attrs, attrs = attrs, attrs.split('\t', 1)[1] if offset else attrs

    var = getVariable(thread_id, frame_id, scope, attrs)

    try:
        _type, _typeName, resolver = get_type(var)
        return _typeName, resolver.get_dictionary(VariableWithOffset(var, offset) if offset else var)
    except:
        sys.stderr.write('Error evaluating: thread_id: %s\nframe_id: %s\nscope: %s\nattrs: %s\n' % (
            thread_id, frame_id, scope, orig_attrs,))
        traceback.print_exc()


def resolve_var_object(var, attrs):
    """
    Resolve variable's attribute

    :param var: an object of variable
    :param attrs: a sequence of variable's attributes separated by \t (i.e.: obj\tattr1\tattr2)
    :return: a value of resolved variable's attribute
    """
    if attrs is not None:
        attr_list = attrs.split('\t')
    else:
        attr_list = []
    for k in attr_list:
        type, _typeName, resolver = get_type(var)
        var = resolver.resolve(var, k)
    return var


def resolve_compound_var_object_fields(var, attrs):
    """
    Resolve compound variable by its object and attributes

    :param var: an object of variable
    :param attrs: a sequence of variable's attributes separated by \t (i.e.: obj\tattr1\tattr2)
    :return: a dictionary of variables's fields
    """
    offset = get_offset(attrs)

    attrs = attrs.split('\t', 1)[1] if offset else attrs

    attr_list = attrs.split('\t')

    for k in attr_list:
        type, _typeName, resolver = get_type(var)
        var = resolver.resolve(var, k)

    try:
        type, _typeName, resolver = get_type(var)
        return resolver.get_dictionary(VariableWithOffset(var, offset) if offset else var)
    except:
        traceback.print_exc()


def custom_operation(thread_id, frame_id, scope, attrs, style, code_or_file, operation_fn_name):
    """
    We'll execute the code_or_file and then search in the namespace the operation_fn_name to execute with the given var.

    code_or_file: either some code (i.e.: from pprint import pprint) or a file to be executed.
    operation_fn_name: the name of the operation to execute after the exec (i.e.: pprint)
    """
    expressionValue = getVariable(thread_id, frame_id, scope, attrs)

    try:
        namespace = {'__name__': '<custom_operation>'}
        if style == "EXECFILE":
            namespace['__file__'] = code_or_file
            execfile(code_or_file, namespace, namespace)
        else:  # style == EXEC
            namespace['__file__'] = '<customOperationCode>'
            Exec(code_or_file, namespace, namespace)

        return str(namespace[operation_fn_name](expressionValue))
    except:
        traceback.print_exc()


def eval_in_context(expression, globals, locals):
    result = None
    try:
        result = eval(expression, globals, locals)
    except Exception:
        s = StringIO()
        traceback.print_exc(file=s)
        result = s.getvalue()

        try:
            try:
                etype, value, tb = sys.exc_info()
                result = value
            finally:
                etype = value = tb = None
        except:
            pass

        result = ExceptionOnEvaluate(result)

        # Ok, we have the initial error message, but let's see if we're dealing with a name mangling error...
        try:
            if '__' in expression:
                # Try to handle '__' name mangling...
                split = expression.split('.')
                curr = locals.get(split[0])
                for entry in split[1:]:
                    if entry.startswith('__') and not hasattr(curr, entry):
                        entry = '_%s%s' % (curr.__class__.__name__, entry)
                    curr = getattr(curr, entry)

                result = curr
        except:
            pass
    return result


def evaluate_expression(thread_id, frame_id, expression, doExec):
    '''returns the result of the evaluated expression
    @param doExec: determines if we should do an exec or an eval
    '''
    frame = find_frame(thread_id, frame_id)
    if frame is None:
        return

    # Not using frame.f_globals because of https://sourceforge.net/tracker2/?func=detail&aid=2541355&group_id=85796&atid=577329
    # (Names not resolved in generator expression in method)
    # See message: http://mail.python.org/pipermail/python-list/2009-January/526522.html
    updated_globals = {}
    updated_globals.update(frame.f_globals)
    updated_globals.update(frame.f_locals)  # locals later because it has precedence over the actual globals

    try:
        expression = str(expression.replace('@LINE@', '\n'))

        if doExec:
            try:
                # try to make it an eval (if it is an eval we can print it, otherwise we'll exec it and
                # it will have whatever the user actually did)
                compiled = compile(expression, '<string>', 'eval')
            except:
                Exec(expression, updated_globals, frame.f_locals)
                pydevd_save_locals.save_locals(frame)
            else:
                result = eval(compiled, updated_globals, frame.f_locals)
                if result is not None:  # Only print if it's not None (as python does)
                    sys.stdout.write('%s\n' % (result,))
            return

        else:
            return eval_in_context(expression, updated_globals, frame.f_locals)
    finally:
        # Should not be kept alive if an exception happens and this frame is kept in the stack.
        del updated_globals
        del frame


def change_attr_expression(thread_id, frame_id, attr, expression, dbg, value=SENTINEL_VALUE):
    '''Changes some attribute in a given frame.
    '''
    frame = find_frame(thread_id, frame_id)
    if frame is None:
        return

    try:
        expression = expression.replace('@LINE@', '\n')

        if dbg.plugin and value is SENTINEL_VALUE:
            result = dbg.plugin.change_variable(frame, attr, expression)
            if result:
                return result

        if value is SENTINEL_VALUE:
            # It is possible to have variables with names like '.0', ',,,foo', etc in scope by setting them with
            # `sys._getframe().f_locals`. In particular, the '.0' variable name is used to denote the list iterator when we stop in
            # list comprehension expressions. This variable evaluates to 0. by `eval`, which is not what we want and this is the main
            # reason we have to check if the expression exists in the global and local scopes before trying to evaluate it.
            value = frame.f_locals.get(expression) or frame.f_globals.get(expression) or eval(expression, frame.f_globals, frame.f_locals)

        if attr[:7] == "Globals":
            attr = attr[8:]
            if attr in frame.f_globals:
                frame.f_globals[attr] = value
                return frame.f_globals[attr]
        else:
            if pydevd_save_locals.is_save_locals_available():
                frame.f_locals[attr] = value
                pydevd_save_locals.save_locals(frame)
                return frame.f_locals[attr]

            # default way (only works for changing it in the topmost frame)
            result = value
            Exec('%s=%s' % (attr, expression), frame.f_globals, frame.f_locals)
            return result

    except Exception:
        traceback.print_exc()


MAXIMUM_ARRAY_SIZE = 100


def array_to_xml(array, name, roffset, coffset, rows, cols, format):
    array, xml, r, c, f = array_to_meta_xml(array, name, format)
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
    xml += array_data_to_xml(rows, cols, lambda r: (get_value(r, c) for c in range(cols)), format)
    return xml


class ExceedingArrayDimensionsException(Exception):
    pass


def array_to_meta_xml(array, name, format):
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
        raise ExceedingArrayDimensionsException()
    elif l == 1:
        # special case with 1D arrays arr[i, :] - row, but arr[:, i] - column with equal shape and ndim
        # http://stackoverflow.com/questions/16837946/numpy-a-2-rows-1-column-file-loadtxt-returns-1row-2-columns
        # explanation: http://stackoverflow.com/questions/15165170/how-do-i-maintain-row-column-orientation-of-vectors-in-numpy?rq=1
        # we use kind of a hack - get information about memory from C_CONTIGUOUS
        is_row = array.flags['C_CONTIGUOUS']

        if is_row:
            rows = 1
            cols = len(array)
            if cols < len(array):
                reslice = '[0:%s]' % (cols)
            array = array[0:cols]
        else:
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
    if type in NUMPY_NUMERIC_TYPES:
        bounds = (array.min(), array.max())
    return array, slice_to_xml(slice, rows, cols, format, type, bounds), rows, cols, format


def array_default_format(type):
    if type == 'f':
        return '.5f'
    elif type == 'i' or type == 'u':
        return 'd'
    else:
        return 's'


def get_label(label):
    return str(label) if not isinstance(label, tuple) else '/'.join(map(str, label))


def dataframe_to_xml(df, name, roffset, coffset, rows, cols, format):
    """
    :type df: pandas.core.frame.DataFrame
    :type name: str
    :type coffset: int
    :type roffset: int
    :type rows: int
    :type cols: int
    :type format: str


    """
    dim = len(df.axes)
    num_rows = df.shape[0]
    num_cols = df.shape[1] if dim > 1 else 1
    format = format.replace('%', '')

    if not format:
        if num_rows > 0 and num_cols == 1:  # series or data frame with one column
            try:
                kind = df.dtype.kind
            except AttributeError:
                try:
                    kind = df.dtypes[0].kind
                except IndexError:
                    kind = 'O'
            format = array_default_format(kind)
        else:
            format = array_default_format('f')

    xml = slice_to_xml(name, num_rows, num_cols, format, "", (0, 0))

    if (rows, cols) == (-1, -1):
        rows, cols = num_rows, num_cols

    rows = min(rows, MAXIMUM_ARRAY_SIZE)
    cols = min(cols, MAXIMUM_ARRAY_SIZE, num_cols)
    # need to precompute column bounds here before slicing!
    col_bounds = [None] * cols
    dtypes = [None] * cols
    if dim > 1:
        for col in range(cols):
            dtype = df.dtypes.iloc[coffset + col].kind
            dtypes[col] = dtype
            if dtype in NUMPY_NUMERIC_TYPES:
                cvalues = df.iloc[:, coffset + col]
                bounds = (cvalues.min(), cvalues.max())
            else:
                bounds = (0, 0)
            col_bounds[col] = bounds
    else:
        dtype = df.dtype.kind
        dtypes[0] = dtype
        col_bounds[0] = (df.min(), df.max()) if dtype in NUMPY_NUMERIC_TYPES else (0, 0)

    df = df.iloc[roffset: roffset + rows, coffset: coffset + cols] if dim > 1 else df.iloc[roffset: roffset + rows]
    rows = df.shape[0]
    cols = df.shape[1] if dim > 1 else 1

    def col_to_format(c):
        return format if dtypes[c] in NUMPY_NUMERIC_TYPES and format else array_default_format(dtypes[c])

    iat = df.iat if dim == 1 or len(df.columns.unique()) == len(df.columns) else df.iloc

    xml += header_data_to_xml(rows, cols, dtypes, col_bounds, col_to_format, df, dim)
    xml += array_data_to_xml(rows, cols, lambda r: (("%" + col_to_format(c)) % (iat[r, c] if dim > 1 else iat[r])
                                                    for c in range(cols)), format)
    return xml


def array_data_to_xml(rows, cols, get_row, format):
    xml = "<arraydata rows=\"%s\" cols=\"%s\"/>\n" % (rows, cols)
    for row in range(rows):
        xml += "<row index=\"%s\"/>\n" % to_string(row)
        for value in get_row(row):
            xml += var_to_xml(value, '', format=format)
    return xml


def slice_to_xml(slice, rows, cols, format, type, bounds):
    return '<array slice=\"%s\" rows=\"%s\" cols=\"%s\" format=\"%s\" type=\"%s\" max=\"%s\" min=\"%s\"/>' % \
           (slice, rows, cols, quote(format), type, bounds[1], bounds[0])


def header_data_to_xml(rows, cols, dtypes, col_bounds, col_to_format, df, dim):
    xml = "<headerdata rows=\"%s\" cols=\"%s\">\n" % (rows, cols)
    for col in range(cols):
        col_label = quote(get_label(df.axes[1].values[col]) if dim > 1 else str(col))
        bounds = col_bounds[col]
        col_format = "%" + col_to_format(col)
        xml += '<colheader index=\"%s\" label=\"%s\" type=\"%s\" format=\"%s\" max=\"%s\" min=\"%s\" />\n' % \
               (str(col), col_label, dtypes[col], col_to_format(col), col_format % bounds[1], col_format % bounds[0])
    for row in range(rows):
        xml += "<rowheader index=\"%s\" label = \"%s\"/>\n" % (str(row), get_label(df.axes[0].values[row]))
    xml += "</headerdata>\n"
    return xml


def is_able_to_format_number(format):
    try:
        format % math.pi
    except Exception:
        return False
    return True


TYPE_TO_XML_CONVERTERS = {"ndarray": array_to_xml, "DataFrame": dataframe_to_xml, "Series": dataframe_to_xml}


def table_like_struct_to_xml(array, name, roffset, coffset, rows, cols, format):
    _, type_name, _ = get_type(array)
    format = format if is_able_to_format_number(format) else '%'
    if type_name in TYPE_TO_XML_CONVERTERS:
        return "<xml>%s</xml>" % TYPE_TO_XML_CONVERTERS[type_name](array, name, roffset, coffset, rows, cols, format)
    else:
        raise VariableError("type %s not supported" % type_name)

