from pydevd_file_utils import GetFileNameAndBaseFromFile
import pydev_log
import traceback

def read_file(filename):
    f = open(filename, "r")
    s = f.read()
    f.close()
    return s


def offset_to_line_number(text, offset):
    curLine = 1
    curOffset = 0
    while curOffset < offset:
        if curOffset == len(text):
            return -1
        c = text[curOffset]
        if c == '\n':
            curLine += 1
        elif c == '\r':
            curLine += 1
            if curOffset < len(text) and text[curOffset + 1] == '\n':
                curOffset += 1

        curOffset += 1

    return curLine


def get_source(frame):
    try:
        node =  frame.f_locals['self']
        if hasattr(node, 'source'):
            return node.source
        else:
            pydev_log.error_once("WARNING: Template path is not available. Please set TEMPLATE_DEBUG=True in your settings.py to make django template breakpoints working")
            return None

    except:
        pydev_log.debug(traceback.format_exc())
        return None


def get_template_file_name(frame):
    try:
        source = get_source(frame)
        if source is None:
            pydev_log.debug("Source is None\n")
            return None
        fname = source[0].name
        pydev_log.debug("Source name is %s\n" % fname)
        filename, base = GetFileNameAndBaseFromFile(fname)
        return filename
    except:
        pydev_log.debug(traceback.format_exc())
        return None


def get_template_line(frame):
    source = get_source(frame)
    file_name = get_template_file_name(frame)
    try:
        return offset_to_line_number(read_file(file_name), source[1][0])
    except:
        return None


class DjangoTemplateFrame:
    def __init__(self, frame):
        file_name = get_template_file_name(frame)
        self.back_context = frame.f_locals['context']
        self.f_code = FCode('Django Template', file_name)
        self.f_lineno = get_template_line(frame)
        self.f_back = frame
        self.f_globals = {}
        self.f_locals = self.collect_context(self.back_context)
        self.f_trace = None

    def collect_context(self, context):
        res = {}
        try:
            for d in context.dicts:
                for k, v in d.items():
                    res[k] = v
        except  AttributeError:
            pass
        return res

    def changeVariable(self, name, value):
        for d in self.back_context.dicts:
            for k, v in d.items():
                if k == name:
                    d[k] = value


class FCode:
    def __init__(self, name, filename):
        self.co_name = name
        self.co_filename = filename


def is_django_exception_break_context(frame):
    try:
        name = frame.f_code.co_name
    except:
        name = None
    return name in ['_resolve_lookup', 'find_template']


def just_raised(trace):
    if trace is None:
        return False
    return trace.tb_next is None

