'''
License: Apache 2.0
Author: Yuli Fitterman
'''
# noinspection PyBroadException
import types

from _pydevd_bundle.pydevd_constants import IS_JYTHON, IS_PY3K, IS_PY311_OR_GREATER

try:
    import inspect
except:
    import traceback
    traceback.print_exc()  # Ok, no inspect available (search will not work)from _pydevd_bundle.pydevd_constants import IS_JYTHON, IS_PY3K

from _pydev_bundle._pydev_imports_tipper import signature_from_docstring


def is_bound_method(obj):
    if isinstance(obj, types.MethodType):
        return getattr(obj, '__self__', getattr(obj, 'im_self', None)) is not None
    else:
        return False


def get_class_name(instance):
    return getattr(getattr(instance, "__class__", None), "__name__", None)


def get_bound_class_name(obj):
    my_self = getattr(obj, '__self__', getattr(obj, 'im_self', None))
    if my_self is None:
        return None
    return get_class_name(my_self)


def getargspec_py2(obj):
    doc_str = obj.__doc__
    args_str = ''

    args = []
    varargs = None
    keywords = None
    defaults = []

    if doc_str:
        lines = doc_str.split('\n')
        if lines:
            func_descr = lines[0]
            s = func_descr.replace(obj.__name__, '')
            idx1 = s.find('(')
            idx2 = s.find(')', idx1)
            if idx1 != -1 and idx2 != -1 and (idx2 > idx1 + 1):
                args_str = s[idx1+1 : idx2]

    if args_str == '':
        return inspect.ArgSpec(args=args, varargs=varargs, keywords=keywords, defaults=tuple(defaults))

    args_list = args_str.split(",")
    for arg in args_list:
        arg = arg.replace(" ", "")
        idx = arg.find("=")

        if idx != -1:
            default = arg[idx+1 :]
            defaults.append(default)
            arg = arg[:idx]

        if arg.startswith("**"):
            keywords = arg
        elif arg.startswith("*"):
            varargs = arg
        else:
            args.append(arg)

    return inspect.ArgSpec(args=args, varargs=varargs, keywords=keywords, defaults=tuple(defaults))


def get_description(obj):
    try:
        ob_call = obj.__call__
    except:
        ob_call = None

    is_init_func = False
    if isinstance(obj, type) or type(obj).__name__ == 'classobj':
        fob = getattr(obj, '__init__', lambda: None)
        is_init_func = True
        if not callable(fob):
            fob = obj
    elif is_bound_method(ob_call):
        fob = ob_call
    else:
        fob = obj

    argspec = ""
    fn_class = None
    if callable(fob):
        try:
            if IS_PY311_OR_GREATER:
                spec_info = inspect.signature(fob)
                argspec = str(spec_info)
            else:
                if IS_PY3K:
                    spec_info = inspect.getfullargspec(fob)
                else:
                    spec_info = inspect.getargspec(fob)
                argspec = inspect.formatargspec(*spec_info)
        except TypeError:
            if is_init_func:
                spec_info = getargspec_py2(obj)
            else:
                spec_info = getargspec_py2(fob)
            argspec = inspect.formatargspec(*spec_info)
        except ValueError:
            # function/method defined in C for example str.count
            argspec = None

        fn_name = getattr(fob, '__name__', None)
        if isinstance(obj, type) or type(obj).__name__ == 'classobj':
            fn_name = "__init__"
            fn_class = getattr(obj, "__name__", "UnknownClass")
        elif is_bound_method(obj) or is_bound_method(ob_call):
            fn_class = get_bound_class_name(obj) or "UnknownClass"

    else:
        fn_name = getattr(fob, '__name__', None)
        fn_self = getattr(fob, '__self__', None)
        if fn_self is not None and not isinstance(fn_self, types.ModuleType):
            fn_class = get_class_name(fn_self)

    doc_string = get_docstring(ob_call) if is_bound_method(ob_call) else get_docstring(obj)
    return create_method_stub(fn_name, fn_class, argspec, doc_string)


def create_method_stub(fn_name, fn_class, argspec, doc_string):
    if fn_name and argspec:
        doc_string = "" if doc_string is None else doc_string
        fn_stub = create_function_stub(fn_name, argspec, doc_string, indent=1 if fn_class else 0)
        if fn_class:
            expr = fn_class if fn_name == '__init__' else fn_class + '().' + fn_name
            return create_class_stub(fn_class, fn_stub) + "\n" + expr
        else:
            expr = fn_name
            return fn_stub + "\n" + expr
    elif doc_string:
        if fn_name:
            restored_signature, _ = signature_from_docstring(doc_string, fn_name)
            if restored_signature:
                return create_method_stub(fn_name, fn_class, restored_signature, doc_string)
        return create_function_stub('unknown', '(*args, **kwargs)', doc_string) + '\nunknown'

    else:
        return ''


def get_docstring(obj):
    if obj is not None:
        try:
            if IS_JYTHON:
                # Jython
                doc = obj.__doc__
                if doc is not None:
                    return doc

                from _pydev_bundle import _pydev_jy_imports_tipper

                is_method, infos = _pydev_jy_imports_tipper.ismethod(obj)
                ret = ''
                if is_method:
                    for info in infos:
                        ret += info.get_as_doc()
                    return ret

            else:

                doc = inspect.getdoc(obj)
                if doc is not None:
                    return doc
        except:
            pass
    else:
        return ''
    try:
        # if no attempt succeeded, try to return repr()...
        return repr(obj)
    except:
        try:
            # otherwise the class
            return str(obj.__class__)
        except:
            # if all fails, go to an empty string
            return ''


def create_class_stub(class_name, contents):
    return "class %s(object):\n%s" % (class_name, contents)


def create_function_stub(fn_name, fn_argspec, fn_docstring, indent=0):
    def shift_right(string, prefix):
        return ''.join(prefix + line for line in string.splitlines(True))

    fn_docstring = shift_right(inspect.cleandoc(fn_docstring), "  " * (indent + 1))
    ret = '''
def %s%s:
    """%s"""
    pass
''' % (fn_name, fn_argspec, fn_docstring)
    ret = ret[1:]  # remove first /n
    ret = ret.replace('\t', "  ")
    if indent:
        prefix = "  " * indent
        ret = shift_right(ret, prefix)
    return ret
