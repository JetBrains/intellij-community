from io import StringIO
import os.path
import sys
import traceback

from _pydevd_bundle.pydevd_collect_try_except_info import collect_try_except_info, collect_return_info
from pydev_tests_python.debugger_unittest import IS_CPYTHON, IS_JYTHON, IS_PY2, IS_PY38_OR_GREATER


def _method_call_with_error():
    try:
        _method_reraise()
    except:
        raise


def _method_except_local():
    Foo = AssertionError
    try:  # SETUP_EXCEPT (to except line)
        raise AssertionError()
    except Foo as exc:
        # DUP_TOP, LOAD_FAST (x), COMPARE_OP (exception match), POP_JUMP_IF_FALSE
        pass


def _method_reraise():
    try:  # SETUP_EXCEPT (to except line)
        raise AssertionError()
    except AssertionError as e:  # POP_TOP
        raise e


def _method_return_with_error():
    _method_call_with_error()


def _method_return_with_error2():
    try:
        _method_call_with_error()
    except:
        return


def _method_simple_raise_any_except():
    try:  # SETUP_EXCEPT (to except line)
        raise AssertionError()
    except:  # POP_TOP
        pass


def _method_simple_raise_any_except_return_on_raise():
    # Note how the tracing the debugger has is equal to the tracing from _method_simple_raise_any_except
    # but this one resulted in an unhandled exception while the other one didn't.
    try:  # SETUP_EXCEPT (to except line)
        raise AssertionError()
    except:  # POP_TOP
        raise  # RAISE_VARARGS


def _method_simple_raise_local_load():
    x = AssertionError
    try:  # SETUP_EXCEPT (to except line)
        raise AssertionError()
    except x as exc:
        # DUP_TOP, LOAD_GLOBAL (NameError), LOAD_GLOBAL(AssertionError), BUILD_TUPLE,
        # COMPARE_OP (exception match), POP_JUMP_IF_FALSE
        pass


def _method_simple_raise_multi_except():
    try:  # SETUP_EXCEPT (to except line)
        raise AssertionError()
    except (NameError, AssertionError) as exc:
        # DUP_TOP, LOAD_FAST (x), COMPARE_OP (exception match), POP_JUMP_IF_FALSE
        pass


def _method_simple_raise_unmatched_except():
    try:  # SETUP_EXCEPT (to except line)
        raise AssertionError()
    except NameError:  # DUP_TOP, LOAD_GLOBAL (NameError), COMPARE_OP (exception match), POP_JUMP_IF_FALSE
        pass


def _method_try_except():
    try:  # SETUP_EXCEPT (to except line)
        try:  # SETUP_EXCEPT (to except line)
            raise AssertionError()
        except:  # POP_TOP
            raise
    except:  # POP_TOP
        return (
            1,
            2
        )


class _Tracer(object):

    def __init__(self, partial_info=False):
        self.partial_info = partial_info
        self.stream = StringIO()
        self._in_print = False

    def tracer_printer(self, frame, event, arg):
        if self._in_print:
            return None
        self._in_print = True
        try:
            if arg is not None:
                if event == 'exception':
                    arg = arg[0].__name__
                elif arg is not None:
                    arg = str(arg)
            if arg is None:
                arg = ''

            if self.partial_info:
                s = ' '.join((
                    os.path.basename(frame.f_code.co_filename),
                    event.upper() if event != 'line' else event,
                    arg,
                ))
            else:
                s = ' '.join((
                    str(frame.f_lineno),
                    frame.f_code.co_name,
                    os.path.basename(frame.f_code.co_filename),
                    event.upper() if event != 'line' else event,
                    arg,
                ))
            self.writeln(s)
        except:
            traceback.print_exc()
        self._in_print = False
        return self.tracer_printer

    def writeln(self, s):
        self.write(s)
        self.write('\n')

    def write(self, s):
        if isinstance(s, bytes):
            s = s.decode('utf-8')
        self.stream.write(s)

    def call(self, c):
        sys.settrace(self.tracer_printer)
        try:
            c()
        except:
            pass
        sys.settrace(None)
        return self.stream.getvalue()


import pytest


@pytest.mark.skipif(not IS_CPYTHON, reason='CPython only test.')
def test_collect_try_except_info(data_regression):
    method_to_info = {}
    for key, method in sorted(dict(globals()).items()):
        if key.startswith('_method'):
            info = collect_try_except_info(method.__code__, use_func_first_line=True)

            if key == "_method_try_except":
                if sys.version_info[:2] == (3, 7):
                    for try_except_info in info:
                        # On 3.7 the last bytecode actually has a different start line.
                        if try_except_info.except_end_line == 8:
                            try_except_info.except_end_line = 9

                elif sys.version_info[:2] >= (3, 8):
                    for try_except_info in info:
                        # On 3.8 the last bytecode actually has a different start line.
                        if try_except_info.except_end_line == 7:
                            try_except_info.except_end_line = 9

            method_to_info[key] = [str(x) for x in info]


            method_to_info[key] = [str(x) for x in info]

    data_regression.check(method_to_info)


def test_collect_try_except_info2():

    def method():
        try:
            raise AssertionError
        except:
            _a = 10
            raise
        finally:
            _b = 20
        _c = 20

    code = method.__code__
    lst = collect_try_except_info(code, use_func_first_line=True)
    if IS_CPYTHON:
        assert str(lst) == '[{try:1 except 3 end block 5 raises: 5}]'
    else:
        assert lst == []


@pytest.mark.skipif(IS_JYTHON, reason='Jython does not have bytecode support.')
def test_collect_return_info():

    def method():
        return 1

    assert str(collect_return_info(method.__code__, use_func_first_line=True)) == '[{return: 1}]'

    def method2():
        pass

    assert str(collect_return_info(method2.__code__, use_func_first_line=True)) == '[{return: 1}]'

    def method3():
        yield 1
        yield 2

    assert str(collect_return_info(method3.__code__, use_func_first_line=True)) == '[{return: 2}]'

    def method4():
        return (1,
                2,
                3,
                4)

    assert str(collect_return_info(method4.__code__, use_func_first_line=True)) == \
           '[{return: 1}]' if IS_PY38_OR_GREATER else '[{return: 4}]'

    def method5():
        return \
 \
            1

    assert str(collect_return_info(method5.__code__, use_func_first_line=True)) == \
           '[{return: 1}]' if IS_PY38_OR_GREATER else '[{return: 3}]'

    if not IS_PY2:
        # return in generator is not valid for python 2.
        code = '''
def method():
    if a:
        yield 1
        yield 2
        return 1
    else:
        pass
'''

        scope = {}
        exec(code, scope)
        assert str(collect_return_info(scope['method'].__code__, use_func_first_line=True)) == \
               '[{return: 4}, {return: 6}]'


def _create_entry(instruction):
    argval = instruction.argval
    return dict(
        opname=instruction.opname,
        argval=argval,
        starts_line=instruction.starts_line,
        is_jump_target=instruction.is_jump_target,
    )


def debug_test_iter_bytecode(data_regression):
    # Note: not run by default, only to help visualizing bytecode and comparing differences among versions.
    import dis

    basename = 'test_iter_bytecode.py%s%s' % (sys.version_info[:2])
    method_to_info = {}
    for key, method in sorted(dict(globals()).items()):
        if key.startswith('_method'):
            info = []

            if sys.version_info[0] < 3:
                from _pydevd_bundle.pydevd_collect_try_except_info import _iter_as_bytecode_as_instructions_py2
                iter_in = _iter_as_bytecode_as_instructions_py2(method.__code__)
            else:
                iter_in = dis.Bytecode(method)

            for instruction in iter_in:
                info.append(_create_entry(instruction))

            msg = []
            for d in info:
                line = []
                for k, v in sorted(d.items()):
                    line.append(u'%s=%s' % (k, v))
                msg.append(u'   '.join(line))
            if isinstance(key, bytes):
                key = key.decode('utf-8')
            method_to_info[key] = msg

    data_regression.check(method_to_info, basename=basename)


def debug_test_tracing_output():  # Note: not run by default, only to debug tracing.
    from collections import defaultdict
    output_to_method_names = defaultdict(list)
    for key, val in sorted(dict(globals()).items()):
        if key.startswith('_method'):
            tracer = _Tracer(partial_info=False)
            output_to_method_names[tracer.call(val)].append(val.__name__)

    # Notes:
    #
    # Seen as the same by the tracing (so, we inspect the bytecode to disambiguate).
    #     _method_simple_raise_any_except
    #     _method_simple_raise_any_except_return_on_raise
    #     _method_simple_raise_multi_except
    #
    # The return with an exception is always None
    #
    # It's not possible to disambiguate from a return None, pass or raise just with the tracing
    # (only a raise with an exception is gotten by the debugger).
    for output, method_names in sorted(output_to_method_names.items(), key=lambda x:(-len(x[1]), ''.join(x[1]))):
        print('=' * 80)
        print('    %s ' % (', '.join(method_names),))
        print('=' * 80)
        print(output)
