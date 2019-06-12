# coding=utf-8

import sys
import traceback
import inspect


_max_reported_output_size = 1 * 1024 * 1024
_reported_output_chunk_size = 50000

PY2 = sys.version_info[0] == 2
if PY2:
    text_type = unicode  # noqa: F821
    binary_type = str
else:
    text_type = str
    binary_type = bytes

_sys_stdout_encoding = sys.stdout.encoding

if PY2:
    from StringIO import StringIO  # noqa: F821
else:
    from io import StringIO

# stdin and stdout encodings should be the same.
# Since stdout may already be monkeypatches, we use stdin
_ENCODING = sys.stdin.encoding if sys.stdin.encoding else "UTF-8"


class FlushingStringIO(StringIO, object):

    encoding = _ENCODING   # stdout must have encoding

    def __init__(self, flush_function):
        super(FlushingStringIO, self).__init__()

        self._flush_function = flush_function
        self.encoding = _ENCODING

    def _flush_to_flush_function(self):
        self._flush_function(self.getvalue())
        self.seek(0)
        self.truncate()

    def write(self, str):
        super(FlushingStringIO, self).write(str)

        if '\n' in str:
            self._flush_to_flush_function()

    def flush(self, *args, **kwargs):
        self._flush_to_flush_function()
        return super(FlushingStringIO, self).flush(*args, **kwargs)


def limit_output(data):
    return data[:_max_reported_output_size]


def split_output(data):
    while len(data) > 0:
        if len(data) <= _reported_output_chunk_size:
            yield data
            data = ''
        else:
            yield data[:_reported_output_chunk_size]
            data = data[_reported_output_chunk_size:]


def dump_test_stdout(messages, test_id, flow_id, data):
    for chunk in split_output(limit_output(data)):
        messages.testStdOut(test_id, chunk, flowId=flow_id)


def dump_test_stderr(messages, test_id, flow_id, data):
    for chunk in split_output(limit_output(data)):
        messages.testStdErr(test_id, chunk, flowId=flow_id)


def is_string(obj):
    if sys.version_info >= (3, 0):
        return isinstance(obj, str)
    else:
        return isinstance(obj, basestring)  # noqa: F821


def get_output_encoding():
    import locale
    loc = locale.getdefaultlocale()
    if loc[1]:
        return loc[1]
    return _sys_stdout_encoding


def get_exception_message(e):
    if e.args and isinstance(e.args[0], binary_type):
        return e.args[0].decode(get_output_encoding())
    return text_type(e)


def to_unicode(obj):
    if isinstance(obj, binary_type):
        return obj.decode(get_output_encoding())
    elif isinstance(obj, text_type):
        return obj
    else:
        if PY2:
            raise TypeError("Expected str or unicode")
        else:
            raise TypeError("Expected bytes or str")


def get_class_fullname(something):
    if inspect.isclass(something):
        cls = something
    else:
        cls = something.__class__

    module = cls.__module__
    if module is None or module == str.__class__.__module__:
        return cls.__name__
    return module + '.' + cls.__name__


def convert_error_to_string(err, frames_to_skip_from_tail=0):
    try:
        if hasattr(err, "type") and hasattr(err, "value") and hasattr(err, "tb"):
            exctype, value, tb = err.type, err.value, err.tb
        else:
            exctype, value, tb = err
        trace = traceback.format_exception(exctype, value, tb)
        if frames_to_skip_from_tail:
            trace = trace[:-frames_to_skip_from_tail]
        return ''.join(trace)
    except Exception:
        tb = traceback.format_exc()
        return "*FAILED TO GET TRACEBACK*: " + tb
