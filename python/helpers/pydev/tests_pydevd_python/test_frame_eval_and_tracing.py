import os
import platform
import unittest
import sys

import time

try:
    from tests_pydevd_python import debugger_unittest
except:
    sys.path.append(os.path.dirname(os.path.dirname(__file__)))

IS_CPYTHON = platform.python_implementation() == 'CPython'
IS_PY36 = sys.version_info[0] == 3 and sys.version_info[1] == 6
TEST_CYTHON = os.getenv('PYDEVD_USE_CYTHON', None) == 'YES'


class WriterThreadStepAndResume(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(10, 'Method2')
        self.write_add_breakpoint(2, 'Method1')
        self.write_make_initial_run()

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('111', True)

        assert line == 10, 'Expected return to be in line 10, was: %s' % line
        assert suspend_type == "frame_eval", 'Expected suspend type to be "frame_eval", but was: %s' % suspend_type

        self.write_step_over(thread_id)
        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('108', True)

        assert line == 11, 'Expected return to be in line 11, was: %s' % line
        # we use tracing debugger while stepping
        assert suspend_type == "trace", 'Expected suspend type to be "trace", but was: %s' % suspend_type

        self.write_run_thread(thread_id)

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('111', True)

        assert line == 2, 'Expected return to be in line 2, was: %s' % line
        # we enable frame evaluation debugger after "Resume" command
        assert suspend_type == "frame_eval", 'Expected suspend type to be "frame_eval", but was: %s' % suspend_type

        self.write_run_thread(thread_id)

        self.finished_ok = True


class WriterThreadStepReturn(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case56.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(2, 'Call2')
        self.write_make_initial_run()

        thread_id, frame_id, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type()

        assert suspend_type == "frame_eval", 'Expected suspend type to be "frame_eval", but was: %s' % suspend_type
        self.write_get_frame(thread_id, frame_id)

        self.write_step_return(thread_id)

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('109', True)

        assert line == 8, 'Expecting it to go to line 8. Went to: %s' % line
        # Step return uses temporary breakpoint, so we use tracing debugger
        assert suspend_type == "trace", 'Expected suspend type to be "trace", but was: %s' % suspend_type

        self.write_step_in(thread_id)

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('107', True)

        # goes to line 4 in jython (function declaration line)
        assert line in (4, 5), 'Expecting it to go to line 4 or 5. Went to: %s' % line
        # we use tracing debugger for stepping
        assert suspend_type == "trace", 'Expected suspend type to be "trace", but was: %s' % suspend_type

        self.write_run_thread(thread_id)

        self.finished_ok = True


class WriterThreadAddLineBreakWhileRun(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case3.py')

    def run(self):
        self.start_socket()
        self.write_make_initial_run()
        time.sleep(.5)
        breakpoint_id = self.write_add_breakpoint(4, '')

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('111', True)

        assert line == 4, 'Expected return to be in line 4, was: %s' % line
        # we use tracing debugger if breakpoint was added while running
        assert suspend_type == "trace", 'Expected suspend type to be "trace", but was: %s' % suspend_type

        self.write_get_frame(thread_id, frame_id)

        self.write_run_thread(thread_id)

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('111', True)
        assert line == 4, 'Expected return to be in line 4, was: %s' % line
        # we still use tracing debugger
        assert suspend_type == "trace", 'Expected suspend type to be "trace", but was: %s' % suspend_type

        self.write_get_frame(thread_id, frame_id)

        self.write_remove_breakpoint(breakpoint_id)

        self.write_run_thread(thread_id)

        self.finished_ok = True


class WriterThreadExceptionBreak(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(10, 'Method2')
        self.write_add_exception_breakpoint_with_policy('IndexError', "1", "0", "0")
        self.write_make_initial_run()
        time.sleep(.5)

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('111', True)

        assert line == 10, 'Expected return to be in line 10, was: %s' % line
        # we use tracing debugger if there are exception breakpoints
        assert suspend_type == "trace", 'Expected suspend type to be "trace", but was: %s' % suspend_type

        self.write_run_thread(thread_id)

        self.finished_ok = True


class WriterThreadAddExceptionBreakWhileRunning(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(10, 'Method2')
        self.write_add_breakpoint(2, 'Method1')
        # self.write_add_exception_breakpoint_with_policy('IndexError', "1", "0", "0")
        self.write_make_initial_run()
        time.sleep(.5)

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('111', True)

        assert line == 10, 'Expected return to be in line 10, was: %s' % line
        # we use tracing debugger if there are exception breakpoints
        assert suspend_type == "frame_eval", 'Expected suspend type to be "frame_eval", but was: %s' % suspend_type

        self.write_add_exception_breakpoint_with_policy('IndexError', "1", "0", "0")

        self.write_run_thread(thread_id)

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('111', True)

        assert line == 2, 'Expected return to be in line 2, was: %s' % line
        # we use tracing debugger if exception break was added
        assert suspend_type == "trace", 'Expected suspend type to be "trace", but was: %s' % suspend_type

        self.write_run_thread(thread_id)

        self.finished_ok = True


class WriterThreadAddTerminationExceptionBreak(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(10, 'Method2')
        self.write_add_exception_breakpoint_with_policy('IndexError', "0", "1", "0")
        self.write_make_initial_run()
        time.sleep(.5)

        thread_id, frame_id, line, suspend_type = self.wait_for_breakpoint_hit_with_suspend_type('111', True)

        assert line == 10, 'Expected return to be in line 10, was: %s' % line
        # we can use frame evaluation with exception breakpoint with "On termination" suspend policy
        assert suspend_type == "frame_eval", 'Expected suspend type to be "frame_eval", but was: %s' % suspend_type

        self.write_run_thread(thread_id)

        self.finished_ok = True


@unittest.skipIf(not IS_PY36 or not IS_CPYTHON or not TEST_CYTHON, reason='Test requires Python 3.6')
class TestFrameEval(unittest.TestCase, debugger_unittest.DebuggerRunner):
    def get_command_line(self):
        return [sys.executable, '-u']

    def test_step_and_resume(self):
        self.check_case(WriterThreadStepAndResume)

    def test_step_return(self):
        self.check_case(WriterThreadStepReturn)

    def test_add_break_while_running(self):
        self.check_case(WriterThreadAddLineBreakWhileRun)

    def test_exc_break(self):
        self.check_case(WriterThreadExceptionBreak)

    def test_add_exc_break_while_running(self):
        self.check_case(WriterThreadAddExceptionBreakWhileRunning)

    def test_add_termination_exc_break(self):
        self.check_case(WriterThreadAddTerminationExceptionBreak)