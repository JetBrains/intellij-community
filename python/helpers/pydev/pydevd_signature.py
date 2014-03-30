import inspect
import trace
import os

trace._warn = lambda *args: None   # workaround for http://bugs.python.org/issue17143 (PY-8706)
import gc
from pydevd_comm import CMD_SIGNATURE_CALL_TRACE, NetCommand
import pydevd_vars

class Signature(object):
    def __init__(self, file, name):
        self.file = file
        self.name = name
        self.args = []
        self.args_str = []

    def add_arg(self, name, type):
        self.args.append((name, type))
        self.args_str.append("%s:%s"%(name, type))

    def __str__(self):
        return "%s %s(%s)"%(self.file, self.name, ", ".join(self.args_str))


class SignatureFactory(object):
    def __init__(self):
        self._caller_cache = {}
        self.project_roots =  os.getenv('PYCHARM_PROJECT_ROOTS', '').split(os.pathsep)

    def is_in_scope(self, filename):
        filename = os.path.normcase(filename)
        for root in self.project_roots:
            root = os.path.normcase(root)
            if filename.startswith(root):
                return True
        return False



    def create_signature(self, frame):
        try:
            code = frame.f_code
            locals = frame.f_locals
            filename, modulename, funcname = self.file_module_function_of(frame)
            res = Signature(filename, funcname)
            for i in range(0, code.co_argcount):
                name = code.co_varnames[i]
                tp = type(locals[name])
                class_name = tp.__name__
                if class_name == 'instance':
                    tp = locals[name].__class__
                    class_name = tp.__name__

                if tp.__module__ and tp.__module__ != '__main__':
                    class_name = "%s.%s"%(tp.__module__, class_name)

                res.add_arg(name, class_name)
            return res
        except:
            import traceback
            traceback.print_exc()


    def file_module_function_of(self, frame): #this code is take from trace module and fixed to work with new-style classes
        code = frame.f_code
        filename = code.co_filename
        if filename:
            modulename = trace.modname(filename)
        else:
            modulename = None

        funcname = code.co_name
        clsname = None
        if code in self._caller_cache:
            if self._caller_cache[code] is not None:
                clsname = self._caller_cache[code]
        else:
            self._caller_cache[code] = None
            ## use of gc.get_referrers() was suggested by Michael Hudson
            # all functions which refer to this code object
            funcs = [f for f in gc.get_referrers(code)
                     if inspect.isfunction(f)]
            # require len(func) == 1 to avoid ambiguity caused by calls to
            # new.function(): "In the face of ambiguity, refuse the
            # temptation to guess."
            if len(funcs) == 1:
                dicts = [d for d in gc.get_referrers(funcs[0])
                         if isinstance(d, dict)]
                if len(dicts) == 1:
                    classes = [c for c in gc.get_referrers(dicts[0])
                               if hasattr(c, "__bases__") or inspect.isclass(c)]
                elif len(dicts) > 1:   #new-style classes
                    classes = [c for c in gc.get_referrers(dicts[1])
                               if hasattr(c, "__bases__") or inspect.isclass(c)]
                else:
                    classes = []

                if len(classes) == 1:
                    # ditto for new.classobj()
                    clsname = classes[0].__name__
                    # cache the result - assumption is that new.* is
                    # not called later to disturb this relationship
                    # _caller_cache could be flushed if functions in
                    # the new module get called.
                    self._caller_cache[code] = clsname


        if clsname is not None:
            funcname = "%s.%s" % (clsname, funcname)

        return filename, modulename, funcname

def create_signature_message(signature):
    cmdTextList = ["<xml>"]

    cmdTextList.append('<call_signature file="%s" name="%s">' % (pydevd_vars.makeValidXmlValue(signature.file), pydevd_vars.makeValidXmlValue(signature.name)))

    for arg in signature.args:
        cmdTextList.append('<arg name="%s" type="%s"></arg>' % (pydevd_vars.makeValidXmlValue(arg[0]), pydevd_vars.makeValidXmlValue(arg[1])))

    cmdTextList.append("</call_signature></xml>")
    cmdText = ''.join(cmdTextList)
    return NetCommand(CMD_SIGNATURE_CALL_TRACE, 0, cmdText)

def sendSignatureCallTrace(dbg, frame, filename):
    if dbg.signature_factory:
        if dbg.signature_factory.is_in_scope(filename):
            dbg.writer.addCommand(create_signature_message(dbg.signature_factory.create_signature(frame)))



