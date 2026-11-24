# coding=utf-8
from __future__ import unicode_literals, print_function

import io
import os
import sys
import threading
import types

from testing import HelpersTestCase, _helpers_root, python3_only

_pydevd_root = os.path.join(_helpers_root, 'debugpy', '_vendored', 'pydevd')


def _divide_by_zero():
    x = 0
    return 1 / x


def _exc_info_of(function):
    try:
        function()
    except Exception:
        return sys.exc_info()
    raise AssertionError('the function did not raise')


def _thread_exception_args(arg):
    exctype, value, tb = arg
    return threading.ExceptHookArgs((exctype, value, tb, None))


class _StubDebugger(object):
    """The part of `PyDB` that `stop_on_unhandled_exception` reads; the stop itself is recorded."""

    def __init__(self, exception_breakpoint):
        self.break_on_uncaught_exceptions = {exception_breakpoint.qname: exception_breakpoint}
        self._exception_breakpoint = exception_breakpoint
        self.stops = []

    def get_exception_breakpoint(self, exctype, exceptions):
        return self._exception_breakpoint

    def ignore_system_exit_code(self, value):
        return False

    def exclude_exception_by_filter(self, exception_breakpoint, tb):
        return False

    def handle_breakpoint_condition(self, additional_info, exception_breakpoint, frame):
        return True

    def handle_breakpoint_expression(self, exception_breakpoint, additional_info, frame):
        pass

    def do_stop_on_unhandled_exception(self, thread, frame, frames_byid, arg):
        self.stops.append(arg)


@python3_only
class PydevdUnhandledExceptionPrintTest(HelpersTestCase):
    """
    PY-94301: the bundled debugpy prints the traceback of an unhandled exception when it stops on it, so the
    Console shows it while the session is paused, and the interpreter does not print it again at exit.
    """

    @classmethod
    def setUpClass(cls):
        super(PydevdUnhandledExceptionPrintTest, cls).setUpClass()
        sys.path.insert(0, _pydevd_root)
        from _pydevd_bundle import pydevd_breakpoints
        cls.pydevd_breakpoints = pydevd_breakpoints

    @classmethod
    def tearDownClass(cls):
        sys.path.remove(_pydevd_root)
        super(PydevdUnhandledExceptionPrintTest, cls).tearDownClass()

    def setUp(self):
        super(PydevdUnhandledExceptionPrintTest, self).setUp()
        self.saved_hooks = (sys.excepthook, threading.excepthook, self.pydevd_breakpoints._exit_hooks)
        self.pydevd_breakpoints._exit_hooks = None
        self.stderr = io.StringIO()
        self.saved_stderr = sys.stderr
        sys.stderr = self.stderr
        breakpoint = self.pydevd_breakpoints.ExceptionBreakpoint(
            'ZeroDivisionError', None, None, False, True, False, False, False)
        self.debugger = _StubDebugger(breakpoint)
        self.additional_info = types.SimpleNamespace()

    def tearDown(self):
        sys.stderr = self.saved_stderr
        sys.excepthook, threading.excepthook, self.pydevd_breakpoints._exit_hooks = self.saved_hooks
        super(PydevdUnhandledExceptionPrintTest, self).tearDown()

    def _stop(self, arg):
        self.pydevd_breakpoints.stop_on_unhandled_exception(
            self.debugger, threading.current_thread(), self.additional_info, arg)

    def test_the_traceback_is_printed_at_the_stop(self):
        arg = _exc_info_of(_divide_by_zero)
        self._stop(arg)
        printed = self.stderr.getvalue()
        self.assertIn('Traceback (most recent call last):', printed)
        self.assertIn('in _divide_by_zero', printed)
        self.assertIn('ZeroDivisionError: division by zero', printed)
        self.assertEqual([arg], self.debugger.stops)

    def test_the_exit_does_not_print_the_exception_again(self):
        arg = _exc_info_of(_divide_by_zero)
        self._stop(arg)
        printed_at_the_stop = self.stderr.getvalue()
        # What the interpreter runs when the exception ends the main thread, and when it ends another thread.
        sys.excepthook(*arg)
        threading.excepthook(_thread_exception_args(arg))
        self.assertEqual(printed_at_the_stop, self.stderr.getvalue())

    def test_a_second_stop_on_the_same_exception_prints_once(self):
        arg = _exc_info_of(_divide_by_zero)
        self._stop(arg)
        self._stop(arg)
        self.assertEqual(1, self.stderr.getvalue().count('Traceback (most recent call last):'))

    def test_an_exception_without_a_stop_still_prints_at_exit(self):
        self._stop(_exc_info_of(_divide_by_zero))
        other = _exc_info_of(lambda: [][1])
        before = self.stderr.getvalue()
        sys.excepthook(*other)
        threading.excepthook(_thread_exception_args(other))
        printed_at_exit = self.stderr.getvalue()[len(before):]
        self.assertEqual(2, printed_at_exit.count('IndexError: list index out of range'))

    def test_the_excepthook_of_the_program_prints_the_stop(self):
        calls = []
        sys.excepthook = lambda exctype, value, tb: calls.append(value)
        arg = _exc_info_of(_divide_by_zero)
        self._stop(arg)
        sys.excepthook(*arg)
        self.assertEqual([arg[1]], calls)
