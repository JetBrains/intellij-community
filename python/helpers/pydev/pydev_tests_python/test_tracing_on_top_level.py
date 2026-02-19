from pydevd import PyDB
import pytest
from pydev_tests_python.debugger_unittest import IS_CPYTHON
from _pydevd_bundle.pydevd_constants import IS_PY39_OR_GREATER
from _pydevd_bundle.pydevd_constants import IS_PY310_OR_GREATER
from _pydevd_bundle.pydevd_constants import IS_PY311_OR_GREATER
from _pydevd_bundle.pydevd_constants import IS_PY313

DEBUG = False


class DummyTopLevelFrame(object):

    __slots__ = ['f_code', 'f_back', 'f_lineno', 'f_trace']

    def __init__(self, method):
        self.f_code = method.__code__
        self.f_back = None
        self.f_lineno = method.__code__.co_firstlineno


class DummyWriter(object):

    __slots__ = ['commands', 'command_meanings']

    def __init__(self):
        self.commands = []
        self.command_meanings = []

    def add_command(self, cmd):
        from _pydevd_bundle.pydevd_comm import ID_TO_MEANING
        meaning = ID_TO_MEANING[str(cmd.id)]
        if DEBUG:
            print(meaning)
        self.command_meanings.append(meaning)
        if DEBUG:
            print(cmd._as_bytes.decode('utf-8'))
        self.commands.append(cmd)


class DummyPyDb(PyDB):

    def __init__(self):
        PyDB.__init__(self, set_as_global=False)

    def do_wait_suspend(
            self, thread, frame, event, arg, *args, **kwargs):
        from _pydevd_bundle.pydevd_constants import STATE_RUN
        info = thread.additional_info
        info.pydev_step_cmd = -1
        info.pydev_step_stop = None
        info.pydev_state = STATE_RUN

        return PyDB.do_wait_suspend(self, thread, frame, event, arg, *args, **kwargs)


class _TraceTopLevel(object):

    def __init__(self):
        self.py_db = DummyPyDb()
        self.py_db.writer = DummyWriter()

    def set_target_func(self, target_func):
        self.frame = DummyTopLevelFrame(target_func)
        self.target_func = target_func

    def get_exception_arg(self):
        import sys
        try:
            raise AssertionError()
        except:
            arg = sys.exc_info()
            return arg

    def create_add_exception_breakpoint_with_policy(
            self, exception, notify_on_handled_exceptions, notify_on_unhandled_exceptions, ignore_libraries):
        return '\t'.join(str(x) for x in [
            exception, notify_on_handled_exceptions, notify_on_unhandled_exceptions, ignore_libraries])

    def add_unhandled_exception_breakpoint(self):
        from _pydevd_bundle.pydevd_process_net_command import process_net_command
        from pydev_tests_python.debugger_unittest import CMD_ADD_EXCEPTION_BREAK
        for exc_name in ('AssertionError', 'RuntimeError'):
            process_net_command(
                self.py_db,
                CMD_ADD_EXCEPTION_BREAK,
                1,
                self.create_add_exception_breakpoint_with_policy(exc_name, '0', '1', '0'),
            )

    def assert_last_commands(self, *commands):
        assert self.py_db.writer.command_meanings[-len(commands):] == list(commands)

    def assert_no_commands(self, *commands):
        for command in commands:
            assert command not in self.py_db.writer.command_meanings

    def trace_dispatch(self, event, arg):
        from _pydevd_bundle import pydevd_trace_dispatch_regular

        self.new_trace_func = pydevd_trace_dispatch_regular.trace_dispatch(self.py_db, self.frame, event, arg)
        return self.new_trace_func

    def call_trace_dispatch(self, line):
        self.frame.f_lineno = line
        return self.trace_dispatch(event='call', arg=None)

    def exception_trace_dispatch(self, line, arg):
        self.frame.f_lineno = line
        self.new_trace_func = self.new_trace_func(self.frame, event='exception', arg=arg)

    def return_trace_dispatch(self, line):
        self.frame.f_lineno = line
        self.new_trace_func = self.new_trace_func(self.frame, event='return', arg=None)

    def assert_paused(self):
        self.assert_last_commands('CMD_THREAD_SUSPEND', 'CMD_THREAD_RUN')

    def assert_not_paused(self):
        self.assert_no_commands('CMD_THREAD_SUSPEND', 'CMD_THREAD_RUN')


@pytest.fixture
def trace_top_level():
    # Note: we trace with a dummy frame with no f_back to simulate the issue in a remote attach.
    return _TraceTopLevel()


@pytest.fixture
def trace_top_level_unhandled(trace_top_level):
    trace_top_level.add_unhandled_exception_breakpoint()
    return trace_top_level


class Handle(object):
    def __init__(self, is_handled=True):
        self.is_handled = is_handled
        self.count = 0

    def __call__(self, func):
        func.__handled__ = self.is_handled
        self.count += 1
        return func


mark_handled = Handle()
mark_unhandled = Handle(False)


#------------------------------------------------------------------------------------------- Handled
@mark_handled
def raise_handled_exception():
    try:
        raise AssertionError()
    except:
        pass


@mark_handled
def raise_handled_exception2():
    try:
        raise AssertionError()
    except AssertionError:
        pass


@mark_handled
def raise_handled_exception3():
    try:
        try:
            raise AssertionError()
        except RuntimeError:
            pass
    except AssertionError:
        pass


@mark_handled
def raise_handled_exception3a():
    try:
        try:
            raise AssertionError()
        except AssertionError:
            pass
    except RuntimeError:
        pass


@mark_handled
def raise_handled_exception4():
    try:
        try:
            raise AssertionError()
        except RuntimeError:
            pass
    except (
        RuntimeError,
        AssertionError):
        pass


@mark_handled
def raise_handled():
    try:
        try:
            raise AssertionError()
        except RuntimeError:
            pass
    except (
        RuntimeError,
        AssertionError):
        pass


@mark_handled
def raise_handled2():
    try:
        raise AssertionError()
    except (
        RuntimeError,
        AssertionError):
        pass

    try:
        raise RuntimeError()
    except (
        RuntimeError,
        AssertionError):
        pass


@mark_handled
def raise_handled9():
    for i in range(2):
        try:
            raise AssertionError()
        except AssertionError:
            if i == 1:
                try:
                    raise
                except:
                    pass


@mark_handled
def raise_handled10():
    for i in range(2):
        try:
            raise AssertionError()
        except AssertionError:
            if i == 1:
                try:
                    raise
                except:
                    pass

    _foo = 10

#----------------------------------------------------------------------------------------- Unhandled


@mark_unhandled
def raise_unhandled_exception():
    raise AssertionError()


@mark_unhandled
def raise_unhandled_exception_not_in_except_clause():
    try:
        raise AssertionError()
    except RuntimeError:
        pass


@mark_unhandled
def raise_unhandled():
    try:
        try:
            raise AssertionError()
        except RuntimeError:
            pass
    except (
        RuntimeError,
        AssertionError):
        raise


@mark_unhandled
def raise_unhandled2():
    try:
        raise AssertionError()
    except AssertionError:
        pass

    raise AssertionError()


@mark_unhandled
def raise_unhandled3():
    try:
        raise AssertionError()
    except AssertionError:
        raise AssertionError()


@mark_unhandled
def raise_unhandled4():
    try:
        raise AssertionError()
    finally:
        _a = 10


@mark_unhandled
def raise_unhandled5():
    try:
        raise AssertionError()
    finally:
        raise RuntimeError()


@mark_unhandled
def raise_unhandled6():
    try:
        raise AssertionError()
    finally:
        raise RuntimeError(
            'in another'
            'line'
        )


@mark_unhandled
def raise_unhandled7():
    try:
        raise AssertionError()
    except AssertionError:
        try:
            raise AssertionError()
        except RuntimeError:
            pass


@mark_unhandled
def raise_unhandled8():
    for i in range(2):

        def get_exc_to_treat():
            if i == 0:
                return AssertionError
            return RuntimeError

        try:
            raise AssertionError()
        except get_exc_to_treat():
            pass


@mark_unhandled
def raise_unhandled9():
    for i in range(2):

        def get_exc_to_treat():
            if i == 0:
                return AssertionError
            return RuntimeError

        try:
            raise AssertionError()
        except get_exc_to_treat():
            try:
                raise
            except:
                pass


@mark_unhandled
def raise_unhandled10():
    for i in range(2):
        try:
            raise AssertionError()
        except AssertionError:
            if i == 1:
                try:
                    raise
                except RuntimeError:
                    pass


@mark_unhandled
def raise_unhandled11():
    try:
        raise_unhandled10()
    finally:
        if True:
            pass


@mark_unhandled
def raise_unhandled12():
    try:
        raise AssertionError()
    except:
        pass
    try:
        raise AssertionError()
    finally:
        if True:
            pass


@mark_unhandled
def reraise_handled_exception():
    try:
        raise AssertionError()  # Should be considered unhandled (because it's reraised).
    except:
        raise


def _collect_events(func):
    collected = []

    def events_collector(frame, event, arg):
        if frame.f_code.co_name == func.__name__:
            collected.append((event, frame.f_lineno, arg))
        return events_collector

    import sys
    sys.settrace(events_collector)
    try:
        func()
    except:
        import traceback;traceback.print_exc()
    finally:
        sys.settrace(None)
    return collected


def _replay_events(collected, trace_top_level_unhandled):
    for event, lineno, arg in collected:
        if event == 'call':
            # Notify only unhandled
            new_trace_func = trace_top_level_unhandled.call_trace_dispatch(lineno)
            # Check that it's dealing with the top-level event.
            assert new_trace_func.__name__ == 'trace_dispatch_and_unhandled_exceptions'
        elif event == 'exception':
            trace_top_level_unhandled.exception_trace_dispatch(lineno, arg)

        elif event == 'return':
            trace_top_level_unhandled.return_trace_dispatch(lineno)

        elif event == 'line':
            pass

        else:
            raise AssertionError('Unexpected: %s' % (event,))


_expected_tested = (
    # handled
    pytest.param(raise_handled_exception, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-853")),
    pytest.param(raise_handled_exception2, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-854")),
    pytest.param(raise_handled_exception3, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-855")),
    pytest.param(raise_handled_exception3a, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-856")),
    pytest.param(raise_handled_exception4, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-857")),
    pytest.param(raise_handled, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-851")),
    pytest.param(raise_handled2, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-852")),
    pytest.param(raise_handled9, marks=pytest.mark.xfail(IS_PY39_OR_GREATER, reason="PCQA-739")),
    pytest.param(raise_handled10, marks=pytest.mark.xfail(IS_PY39_OR_GREATER, reason="PCQA-738")),

    # unhandled
    pytest.param(raise_unhandled_exception, marks=pytest.mark.xfail(IS_PY313, reason="PCQA-885")),
    pytest.param(raise_unhandled_exception_not_in_except_clause, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-863")),
    pytest.param(raise_unhandled, marks=pytest.mark.xfail(IS_PY310_OR_GREATER, reason="PCQA-781")),
    pytest.param(raise_unhandled2, marks=pytest.mark.xfail(IS_PY313, reason="PCQA-881")),
    pytest.param(raise_unhandled2, marks=pytest.mark.xfail(IS_PY313, reason="PCQA-882")),
    pytest.param(raise_unhandled4, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-861")),
    pytest.param(raise_unhandled5, marks=pytest.mark.xfail(IS_PY313, reason="PCQA-883")),
    pytest.param(raise_unhandled6, marks=pytest.mark.xfail(IS_PY313, reason="PCQA-884")),
    pytest.param(raise_unhandled7, marks=pytest.mark.xfail(IS_PY310_OR_GREATER, reason="PCQA-782")),
    pytest.param(raise_unhandled8, marks=pytest.mark.xfail(IS_PY310_OR_GREATER, reason="PCQA-783")),
    pytest.param(raise_unhandled9, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-862")),
    pytest.param(raise_unhandled10, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-858")),
    pytest.param(raise_unhandled11, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-859")),
    pytest.param(raise_unhandled12, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-860")),
    pytest.param(reraise_handled_exception, marks=pytest.mark.xfail(IS_PY311_OR_GREATER, reason="PCQA-864")),
)


def test_expected_number_tested():
    assert len(_expected_tested) == mark_handled.count + mark_unhandled.count


@pytest.mark.skipif(not IS_CPYTHON, reason='try..except info only available on CPython')
@pytest.mark.parametrize("func", _expected_tested)
def test_tracing_on_top_level_unhandled(trace_top_level_unhandled, func):
    trace_top_level_unhandled.set_target_func(func)

    collected_events = _collect_events(func)
    _replay_events(collected_events, trace_top_level_unhandled)

    if func.__handled__:
        trace_top_level_unhandled.assert_not_paused()  # handled exception
    else:
        trace_top_level_unhandled.assert_paused()
