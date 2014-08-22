
from pydevd_plugins.django_frame import FCode
from pydevd_file_utils import GetFileNameAndBaseFromFile
from runfiles import DictContains

class Jinja2TemplateFrame:

    def __init__(self, frame):
        file_name = get_jinja2_template_filename(frame)
        self.back_context = None
        if 'context' in frame.f_locals:
            #sometimes we don't have 'context', e.g. in macros
            self.back_context = frame.f_locals['context']
        self.f_code = FCode('template', file_name)
        self.f_lineno = get_jinja2_template_line(frame)
        self.f_back = find_render_function_frame(frame)
        self.f_globals = {}
        self.f_locals = self.collect_context(frame)
        self.f_trace = None

    def collect_context(self, frame):
        res = {}
        if self.back_context is not None:
            for k, v in self.back_context.items():
                res[k] = v
        for k, v in frame.f_locals.items():
            if not k.startswith('l_'):
                if not k in res:
                    #local variables should shadow globals from context
                    res[k] = v
            elif v and not is_missing(v):
                res[k[2:]] = v
        return res

    def changeVariable(self, name, value):
        for k, v in self.back_context.items():
            if k == name:
                self.back_context.vars[k] = value

def is_missing(item):
    if item.__class__.__name__ is 'MissingType':
        return True
    return False

def find_render_function_frame(frame):
    #in order to hide internal rendering functions
    old_frame = frame
    try:
        while not (DictContains(frame.f_locals, 'self') and frame.f_locals['self'].__class__.__name__ == 'Template' and \
                frame.f_code.co_name == 'render'):
            frame = frame.f_back
            if frame is None:
                return old_frame
        return frame
    except:
        return old_frame

def get_jinja2_template_line(frame):
    if DictContains(frame.f_globals,'__jinja_template__'):
        debug_info = frame.f_globals['__jinja_template__'].debug_info

    if debug_info is None:
        return None

    lineno = frame.f_lineno

    for pair in debug_info:
        if pair[1] == lineno:
            return pair[0]

    return None

def get_jinja2_template_filename(frame):
    if DictContains(frame.f_globals, '__jinja_template__'):
        fname = frame.f_globals['__jinja_template__'].filename
        filename, base = GetFileNameAndBaseFromFile(fname)
        return filename
    return None


