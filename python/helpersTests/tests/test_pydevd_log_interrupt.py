# coding=utf-8
from __future__ import unicode_literals, print_function

import os
import sys

from testing import HelpersTestCase, _helpers_root, python3_only

_pydevd_root = os.path.join(_helpers_root, 'debugpy', '_vendored', 'pydevd')


class _RaisingStream(object):
    """A log stream whose write raises the given error, as a KeyboardInterrupt arriving inside the write does."""

    def __init__(self, error):
        self.error = error

    def write(self, text):
        raise self.error

    def flush(self):
        pass


@python3_only
class PydevdLogInterruptTest(HelpersTestCase):
    """
    PY-92486: with the debugger logs on, pydevd logs every message of a Debug Console command, so the
    KeyboardInterrupt of Ctrl+C mostly arrives inside that logging. A bare except there swallowed it,
    and the command ran to completion.
    """

    @classmethod
    def setUpClass(cls):
        super(PydevdLogInterruptTest, cls).setUpClass()
        sys.path.insert(0, _pydevd_root)
        from _pydev_bundle import pydev_log
        from _pydevd_bundle.pydevd_net_command import NetCommand
        cls.pydev_log = pydev_log
        cls.NetCommand = NetCommand

    @classmethod
    def tearDownClass(cls):
        sys.path.remove(_pydevd_root)
        super(PydevdLogInterruptTest, cls).tearDownClass()

    def test_interrupt_inside_a_log_write_reaches_the_caller(self):
        with self.pydev_log.log_context(1, _RaisingStream(KeyboardInterrupt())):
            with self.assertRaises(KeyboardInterrupt):
                self.pydev_log.critical("a line of the command being logged")

    def test_interrupt_while_tracing_a_sent_command_reaches_the_caller(self):
        with self.pydev_log.log_context(1, _RaisingStream(KeyboardInterrupt())):
            with self.assertRaises(KeyboardInterrupt):
                self.NetCommand._show_debug_info(1, 1, "an output event of the command")
        # The recursion guard of the trace is released on the way out, so the next message is traced again.
        self.assertEqual(0, self.NetCommand._showing_debug_info)

    def test_a_broken_log_stream_is_still_ignored(self):
        # The excepts exist for a log stream that is gone, and that part stays.
        with self.pydev_log.log_context(1, _RaisingStream(OSError("stream closed"))):
            self.pydev_log.critical("a line of the command being logged")
            self.NetCommand._show_debug_info(1, 1, "an output event of the command")
        self.assertEqual(0, self.NetCommand._showing_debug_info)
