from collections import namedtuple
from contextlib import contextmanager
import json
try:
    from urllib import quote, quote_plus, unquote_plus
except ImportError:
    from urllib.parse import quote, quote_plus, unquote_plus  # @UnresolvedImport

import re
import socket
import subprocess
import threading
import time
import traceback
from pydev_tests_python.debug_constants import *

from _pydev_bundle import pydev_localhost



# Note: copied (don't import because we want it to be independent on the actual code because of backward compatibility).
CMD_RUN = 101
CMD_LIST_THREADS = 102
CMD_THREAD_CREATE = 103
CMD_THREAD_KILL = 104
CMD_THREAD_SUSPEND = 105
CMD_THREAD_RUN = 106
CMD_STEP_INTO = 107
CMD_STEP_OVER = 108
CMD_STEP_RETURN = 109
CMD_GET_VARIABLE = 110
CMD_SET_BREAK = 111
CMD_REMOVE_BREAK = 112
CMD_EVALUATE_EXPRESSION = 113
CMD_GET_FRAME = 114
CMD_EXEC_EXPRESSION = 115
CMD_WRITE_TO_CONSOLE = 116
CMD_CHANGE_VARIABLE = 117
CMD_RUN_TO_LINE = 118
CMD_RELOAD_CODE = 119
CMD_GET_COMPLETIONS = 120

# Note: renumbered (conflicted on merge)
CMD_CONSOLE_EXEC = 121
CMD_ADD_EXCEPTION_BREAK = 122
CMD_REMOVE_EXCEPTION_BREAK = 123
CMD_LOAD_SOURCE = 124
CMD_ADD_DJANGO_EXCEPTION_BREAK = 125
CMD_REMOVE_DJANGO_EXCEPTION_BREAK = 126
CMD_SET_NEXT_STATEMENT = 127
CMD_SMART_STEP_INTO = 128
CMD_EXIT = 129
CMD_SIGNATURE_CALL_TRACE = 130

CMD_SET_PY_EXCEPTION = 131
CMD_GET_FILE_CONTENTS = 132
CMD_SET_PROPERTY_TRACE = 133
# Pydev debug console commands
CMD_EVALUATE_CONSOLE_EXPRESSION = 134
CMD_RUN_CUSTOM_OPERATION = 135
CMD_GET_BREAKPOINT_EXCEPTION = 136
CMD_STEP_CAUGHT_EXCEPTION = 137
CMD_SEND_CURR_EXCEPTION_TRACE = 138
CMD_SEND_CURR_EXCEPTION_TRACE_PROCEEDED = 139
CMD_IGNORE_THROWN_EXCEPTION_AT = 140
CMD_ENABLE_DONT_TRACE = 141
CMD_SHOW_CONSOLE = 142

CMD_GET_ARRAY = 143
CMD_STEP_INTO_MY_CODE = 144
CMD_GET_CONCURRENCY_EVENT = 145
CMD_SHOW_RETURN_VALUES = 146

CMD_GET_THREAD_STACK = 152
CMD_THREAD_DUMP_TO_STDERR = 153  # This is mostly for unit-tests to diagnose errors on ci.
CMD_STOP_ON_START = 154
CMD_GET_EXCEPTION_DETAILS = 155
CMD_PYDEVD_JSON_CONFIG = 156

CMD_THREAD_SUSPEND_SINGLE_NOTIFICATION = 157
CMD_THREAD_RESUME_SINGLE_NOTIFICATION = 158

CMD_REDIRECT_OUTPUT = 200
CMD_GET_NEXT_STATEMENT_TARGETS = 201
CMD_SET_PROJECT_ROOTS = 202

CMD_VERSION = 501
CMD_RETURN = 502
CMD_ERROR = 901

REASON_CAUGHT_EXCEPTION = CMD_STEP_CAUGHT_EXCEPTION
REASON_UNCAUGHT_EXCEPTION = CMD_ADD_EXCEPTION_BREAK
REASON_STOP_ON_BREAKPOINT = CMD_SET_BREAK
REASON_THREAD_SUSPEND = CMD_THREAD_SUSPEND
REASON_STEP_INTO = CMD_STEP_INTO
REASON_STEP_INTO_MY_CODE = CMD_STEP_INTO_MY_CODE
REASON_STEP_OVER = CMD_STEP_OVER

# Always True (because otherwise when we do have an error, it's hard to diagnose).
SHOW_WRITES_AND_READS = True
SHOW_OTHER_DEBUG_INFO = True
SHOW_STDOUT = True

import platform

IS_CPYTHON = platform.python_implementation() == 'CPython'
IS_IRONPYTHON = platform.python_implementation() == 'IronPython'
IS_JYTHON = platform.python_implementation() == 'Jython'
IS_APPVEYOR = os.environ.get('APPVEYOR', '') in ('True', 'true', '1')

IS_PY37_OR_GREATER = sys.version_info >= (3, 7)
IS_PY38_OR_GREATER = sys.version_info >= (3, 8)

try:
    from thread import start_new_thread
except ImportError:
    from _thread import start_new_thread  # @UnresolvedImport

try:
    xrange
except:
    xrange = range

Hit = namedtuple('Hit', 'thread_id, frame_id, line, suspend_type, name, file')


def overrides(method):
    '''
    Helper to check that one method overrides another (redeclared in unit-tests to avoid importing pydevd).
    '''

    def wrapper(func):
        if func.__name__ != method.__name__:
            msg = "Wrong @override: %r expected, but overwriting %r."
            msg = msg % (func.__name__, method.__name__)
            raise AssertionError(msg)

        if func.__doc__ is None:
            func.__doc__ = method.__doc__

        return func

    return wrapper


TIMEOUT = 20


class TimeoutError(RuntimeError):
    pass


def wait_for_condition(condition, msg=None, timeout=TIMEOUT, sleep=.05):
    curtime = time.time()
    while True:
        if condition():
            break
        if time.time() - curtime > timeout:
            error_msg = 'Condition not reached in %s seconds' % (timeout,)
            if msg is not None:
                error_msg += '\n'
                if callable(msg):
                    error_msg += msg()
                else:
                    error_msg += str(msg)

            raise TimeoutError(error_msg)
        time.sleep(sleep)


#=======================================================================================================================
# ReaderThread
#=======================================================================================================================
class ReaderThread(threading.Thread):

    MESSAGES_TIMEOUT = 15

    def __init__(self, sock):
        threading.Thread.__init__(self)
        try:
            from queue import Queue
        except ImportError:
            from Queue import Queue

        self.setDaemon(True)
        self.sock = sock
        self._queue = Queue()
        self.all_received = []
        self._kill = False

    def set_messages_timeout(self, timeout):
        self.MESSAGES_TIMEOUT = timeout

    def get_next_message(self, context_message, timeout=None):
        if timeout is None:
            timeout = self.MESSAGES_TIMEOUT
        try:
            msg = self._queue.get(block=True, timeout=timeout)
        except:
            raise TimeoutError('No message was written in %s seconds. Error message:\n%s' % (timeout, context_message,))
        else:
            frame = sys._getframe().f_back.f_back
            frame_info = ''
            while frame:
                if frame.f_code.co_filename.endswith('debugger_unittest.py'):
                    frame = frame.f_back
                    continue
                stack_msg = ' --  File "%s", line %s, in %s\n' % (frame.f_code.co_filename, frame.f_lineno, frame.f_code.co_name)
                if 'run' == frame.f_code.co_name:
                    frame_info = stack_msg  # Ok, found the writer thread 'run' method (show only that).
                    break
                frame_info += stack_msg
                frame = frame.f_back
                # Just print the first which is not debugger_unittest.py
                break

            frame = None
            sys.stdout.write('Message returned in get_next_message(): %s --  ctx: %s, asked at:\n%s\n' % (unquote_plus(unquote_plus(msg)), context_message, frame_info))
        return msg

    def run(self):
        try:
            buf = ''
            while not self._kill:
                l = self.sock.recv(1024)
                if IS_PY3K:
                    l = l.decode('utf-8')
                self.all_received.append(l)
                buf += l

                while '\n' in buf:
                    # Print each part...
                    i = buf.index('\n') + 1
                    last_received = buf[:i]
                    buf = buf[i:]

                    if SHOW_WRITES_AND_READS:
                        print('Test Reader Thread Received %s' % (last_received,))

                    self._queue.put(last_received)
        except:
            pass  # ok, finished it
        finally:
            del self.all_received[:]

    def do_kill(self):
        self._kill = True
        if hasattr(self, 'sock'):
            self.sock.close()


def read_process(stream, buffer, debug_stream, stream_name, finish):
    while True:
        line = stream.readline()
        if not line:
            break

        if IS_PY3K:
            line = line.decode('utf-8', errors='replace')

        if SHOW_STDOUT:
            debug_stream.write('%s: %s' % (stream_name, line,))
        buffer.append(line)

        if finish[0]:
            return


def start_in_daemon_thread(target, args):
    t0 = threading.Thread(target=target, args=args)
    t0.setDaemon(True)
    t0.start()


class DebuggerRunner(object):

    def get_command_line(self):
        '''
        Returns the base command line (i.e.: ['python.exe', '-u'])
        '''
        raise NotImplementedError

    def add_command_line_args(self, args):
        writer = self.writer
        port = int(writer.port)

        localhost = pydev_localhost.get_localhost()
        ret = [
            writer.get_pydevd_file(),
            '--DEBUG_RECORD_SOCKET_READS',
            '--qt-support',
            '--client',
            localhost,
            '--port',
            str(port),
        ]

        if writer.IS_MODULE:
            ret += ['--module']

        ret += ['--file'] + writer.get_command_line_args()
        ret = writer.update_command_line_args(ret)  # Provide a hook for the writer
        return args + ret

    @contextmanager
    def check_case(self, writer_class):
        if callable(writer_class):
            writer = writer_class()
        else:
            writer = writer_class
        try:
            writer.start()
            wait_for_condition(lambda: hasattr(writer, 'port'))
            self.writer = writer

            args = self.get_command_line()

            args = self.add_command_line_args(args)

            if SHOW_OTHER_DEBUG_INFO:
                print('executing', ' '.join(args))

            with self.run_process(args, writer) as dct_with_stdout_stder:
                try:
                    wait_for_condition(lambda: writer.finished_initialization)
                except TimeoutError:
                    sys.stderr.write('Timed out waiting for initialization\n')
                    sys.stderr.write('stdout:\n%s\n\nstderr:\n%s\n' % (
                        ''.join(dct_with_stdout_stder['stdout']),
                        ''.join(dct_with_stdout_stder['stderr']),
                    ))
                    raise
                finally:
                    writer.get_stdout = lambda: ''.join(dct_with_stdout_stder['stdout'])
                    writer.get_stderr = lambda: ''.join(dct_with_stdout_stder['stderr'])

                yield writer
        finally:
            writer.do_kill()
            writer.log = []

        stdout = dct_with_stdout_stder['stdout']
        stderr = dct_with_stdout_stder['stderr']
        writer.additional_output_checks(''.join(stdout), ''.join(stderr))

    def create_process(self, args, writer):
        process = subprocess.Popen(
            args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            cwd=writer.get_cwd() if writer is not None else '.',
            env=writer.get_environ() if writer is not None else None,
        )
        return process

    @contextmanager
    def run_process(self, args, writer):
        process = self.create_process(args, writer)
        stdout = []
        stderr = []
        finish = [False]
        dct_with_stdout_stder = {}

        try:
            start_in_daemon_thread(read_process, (process.stdout, stdout, sys.stdout, 'stdout', finish))
            start_in_daemon_thread(read_process, (process.stderr, stderr, sys.stderr, 'stderr', finish))

            if SHOW_OTHER_DEBUG_INFO:
                print('Both processes started')

            # polls can fail (because the process may finish and the thread still not -- so, we give it some more chances to
            # finish successfully).
            initial_time = time.time()
            shown_intermediate = False
            dumped_threads = False

            dct_with_stdout_stder['stdout'] = stdout
            dct_with_stdout_stder['stderr'] = stderr
            yield dct_with_stdout_stder

            if not writer.finished_ok:
                self.fail_with_message(
                    "The thread that was doing the tests didn't finish successfully.", stdout, stderr, writer)

            while True:
                if process.poll() is not None:
                    if writer.EXPECTED_RETURNCODE != 'any':
                        expected_returncode = writer.EXPECTED_RETURNCODE
                        if not isinstance(expected_returncode, (list, tuple)):
                            expected_returncode = (expected_returncode,)

                        if process.returncode not in expected_returncode:
                            self.fail_with_message('Expected process.returncode to be %s. Found: %s' % (
                                writer.EXPECTED_RETURNCODE, process.returncode), stdout, stderr, writer)
                    break
                else:
                    if writer is not None:
                        if writer.FORCE_KILL_PROCESS_WHEN_FINISHED_OK:
                            process.kill()
                            continue

                        if not shown_intermediate and (time.time() - initial_time > (TIMEOUT / 3.)):  # 1/3 of timeout
                            print('Warning: writer thread exited and process still did not (%.2fs seconds elapsed).' % (time.time() - initial_time,))
                            shown_intermediate = True

                        if time.time() - initial_time > ((TIMEOUT / 3.) * 2.):  # 2/3 of timeout
                            if not dumped_threads:
                                dumped_threads = True
                                # It still didn't finish. Ask for a thread dump
                                # (we'll be able to see it later on the test output stderr).
                                try:
                                    writer.write_dump_threads()
                                except:
                                    traceback.print_exc()

                        if time.time() - initial_time > TIMEOUT:  # timed out
                            process.kill()
                            time.sleep(.2)
                            self.fail_with_message(
                                "The other process should've exited but still didn't (%.2fs seconds timeout for process to exit)." % (time.time() - initial_time,),
                                stdout, stderr, writer
                            )
                time.sleep(.2)

            if writer is not None:
                if not writer.FORCE_KILL_PROCESS_WHEN_FINISHED_OK:
                    if stdout is None:
                        self.fail_with_message(
                            "The other process may still be running -- and didn't give any output.", stdout, stderr, writer)

                    check = 0
                    while not writer.check_test_suceeded_msg(stdout, stderr):
                        check += 1
                        if check == 50:
                            self.fail_with_message("TEST SUCEEDED not found.", stdout, stderr, writer)
                        time.sleep(.1)

        except TimeoutError:
            writer.write_dump_threads()
            time.sleep(.2)
            raise
        finally:
            finish[0] = True

    def fail_with_message(self, msg, stdout, stderr, writerThread):
        raise AssertionError(msg +
            "\n\n===========================\nStdout: \n" + ''.join(stdout) +
            "\n\n===========================\nStderr:" + ''.join(stderr) +
            "\n\n===========================\nLog:\n" + '\n'.join(getattr(writerThread, 'log', [])))


# noinspection PyProtectedMember
def _create_socket(port=None):
    if hasattr(_create_socket, '_used_ports'):
        assert port not in _create_socket._used_ports, 'Socket already initialized.'
    else:
        _create_socket._used_ports = set()

    if port is not None:
        _create_socket._used_ports.add(port)

    from _pydev_bundle.pydev_localhost import get_socket_name
    if SHOW_WRITES_AND_READS:
        print('start_socket')

    if port is None:
        socket_name = get_socket_name(close=True)
    else:
        socket_name = (pydev_localhost.get_localhost(), port)
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(socket_name)
    return sock


#=======================================================================================================================
# AbstractWriterThread
#=======================================================================================================================
class AbstractWriterThread(threading.Thread):

    FORCE_KILL_PROCESS_WHEN_FINISHED_OK = False
    IS_MODULE = False
    TEST_FILE = None
    EXPECTED_RETURNCODE = 0

    def __init__(self, *args, **kwargs):
        threading.Thread.__init__(self, *args, **kwargs)
        self.setDaemon(True)
        self.finished_ok = False
        self.finished_initialization = False
        self._next_breakpoint_id = 0
        self.log = []

    def run(self):
        self.start_socket()

    def check_test_suceeded_msg(self, stdout, stderr):
        return 'TEST SUCEEDED' in ''.join(stdout)

    def update_command_line_args(self, args):
        return args

    def _ignore_stderr_line(self, line):
        if line.startswith((
            'debugger: ',
            '>>',
            '<<',
            'warning: Debugger speedups',
            'pydev debugger: New process is launching',
            'pydev debugger: To debug that process',
            'pydev debugger: process'
        )):
            return True

        if re.match(r'^(\d+)\t(\d)+', line):
            return True

        if IS_JYTHON:
            for expected in (
                'org.python.netty.util.concurrent.DefaultPromise',
                'org.python.netty.util.concurrent.SingleThreadEventExecutor',
                'Failed to submit a listener notification task. Event loop shut down?',
                'java.util.concurrent.RejectedExecutionException',
                'An event executor terminated with non-empty task',
                'java.lang.UnsupportedOperationException',
                "RuntimeWarning: Parent module '_pydevd_bundle' not found while handling absolute import",
                'from _pydevd_bundle.pydevd_additional_thread_info_regular import _current_frames',
                'import org.python.core as PyCore #@UnresolvedImport',
                'from _pydevd_bundle.pydevd_additional_thread_info import set_additional_thread_info',
                ):
                if expected in line:
                    return True

            if line.strip().startswith('at '):
                return True

        if IS_PY26:
            # Sometimes in the ci there's an unhandled exception which doesn't have a stack trace
            # (apparently this happens when a daemon thread dies during process shutdown).
            # This was only reproducible on the ci on Python 2.6, so, ignoring that output on Python 2.6 only.
            for expected in (
                'Unhandled exception in thread started by <_pydev_bundle.pydev_monkey._NewThreadStartupWithTrace'):
                if expected in line:
                    return True
        return False

    def additional_output_checks(self, stdout, stderr):
        lines_with_error = []
        for line in stderr.splitlines():
            line = line.strip()
            if not line:
                continue
            if not self._ignore_stderr_line(line):
                lines_with_error.append(line)

        if lines_with_error:
            raise AssertionError('Did not expect to have line(s) in stderr:\n\n%s\n\nFull stderr:\n\n%s' % (
                '\n'.join(lines_with_error), stderr))

    def get_environ(self):
        return None

    def get_pydevd_file(self):
        dirname = os.path.dirname(__file__)
        dirname = os.path.dirname(dirname)
        return os.path.abspath(os.path.join(dirname, 'pydevd.py'))

    def get_line_index_with_content(self, line_content):
        '''
        :return the line index which has the given content (1-based).
        '''
        with open(self.TEST_FILE, 'r') as stream:
            for i_line, line in enumerate(stream):
                if line_content in line:
                    return i_line + 1
        raise AssertionError('Did not find: %s in %s' % (line_content, self.TEST_FILE))

    def get_cwd(self):
        return os.path.dirname(self.get_pydevd_file())

    def get_command_line_args(self):
        return [self.TEST_FILE]

    def do_kill(self):
        if hasattr(self, 'server_socket'):
            self.server_socket.close()

        if hasattr(self, 'reader_thread'):
            # if it's not created, it's not there...
            self.reader_thread.do_kill()
        if hasattr(self, 'sock'):
            self.sock.close()

    def write(self, s):
        from _pydevd_bundle.pydevd_comm import ID_TO_MEANING
        meaning = ID_TO_MEANING.get(re.search(r'\d+', s).group(), '')
        if meaning:
            meaning += ': '

        self.log.append('write: %s%s' % (meaning, s,))

        if SHOW_WRITES_AND_READS:
            print('Test Writer Thread Written %s%s' % (meaning, s,))
        msg = s + '\n'

        if not hasattr(self, 'sock'):
            print('%s.sock not available when sending: %s' % (self, msg))
            return

        if IS_PY3K:
            msg = msg.encode('utf-8')

        self.sock.send(msg)

    def get_next_message(self, context_message, timeout=None):
        return self.reader_thread.get_next_message(context_message, timeout=timeout)

    def start_socket(self, port=None):
        self._sequence = -1
        server_socket = _create_socket(port)
        self.port = server_socket.getsockname()[1]
        server_socket.listen(1)
        if SHOW_WRITES_AND_READS:
            print('Waiting in socket.accept()')
        self.server_socket = server_socket
        new_sock, addr = server_socket.accept()
        if SHOW_WRITES_AND_READS:
            print('Test Writer Thread Socket:', new_sock, addr)

        reader_thread = self.reader_thread = ReaderThread(new_sock)
        reader_thread.start()
        self.sock = new_sock

        # initial command is always the version
        self.write_version()
        self.log.append('start_socket')
        self.finished_initialization = True

    def next_breakpoint_id(self):
        self._next_breakpoint_id += 1
        return self._next_breakpoint_id

    def next_seq(self):
        self._sequence += 2
        return self._sequence

    def wait_for_new_thread(self):
        # wait for hit breakpoint
        last = ''
        while not '<xml><thread name="' in last or '<xml><thread name="pydevd.' in last:
            last = self.get_next_message('wait_for_new_thread')

        # we have something like <xml><thread name="MainThread" id="12103472" /></xml>
        splitted = last.split('"')
        thread_id = splitted[3]
        return thread_id

    def wait_for_output(self):
        # Something as:
        # <xml><io s="TEST SUCEEDED%2521" ctx="1"/></xml>
        while True:
            msg = self.get_next_message('wait_output')
            if "<xml><io s=" in msg:
                if 'ctx="1"' in msg:
                    ctx = 'stdout'
                elif 'ctx="2"' in msg:
                    ctx = 'stderr'
                else:
                    raise AssertionError('IO message without ctx.')

                msg = unquote_plus(unquote_plus(msg.split('"')[1]))
                return msg, ctx

    def get_current_stack_hit(self, thread_id, **kwargs):
        self.write_get_thread_stack(thread_id)
        msg = self.wait_for_message(CMD_GET_THREAD_STACK)
        return self._get_stack_as_hit(msg, **kwargs)

    def wait_for_single_notification_as_hit(self, reason=REASON_STOP_ON_BREAKPOINT, **kwargs):
        dct = self.wait_for_json_message(CMD_THREAD_SUSPEND_SINGLE_NOTIFICATION)
        assert dct['stop_reason'] == reason

        line = kwargs.pop('line', None)
        file = kwargs.pop('file', None)
        assert not kwargs, 'Unexpected kwargs: %s' % (kwargs,)

        return self.get_current_stack_hit(dct['thread_id'], line=line, file=file)

    def wait_for_breakpoint_hit(self, reason=REASON_STOP_ON_BREAKPOINT, timeout=None, **kwargs):
        '''
        108 is over
        109 is return
        111 is breakpoint

        :param reason: may be the actual reason (int or string) or a list of reasons.
        '''
        # note: those must be passed in kwargs.
        line = kwargs.get('line')
        file = kwargs.get('file')

        self.log.append('Start: wait_for_breakpoint_hit')
        # wait for hit breakpoint
        if not isinstance(reason, (list, tuple)):
            reason = (reason,)

        def accept_message(last):
            for r in reason:
                if ('stop_reason="%s"' % (r,)) in last:
                    return True
            return False

        msg = self.wait_for_message(accept_message, timeout=timeout)
        return self._get_stack_as_hit(msg, file, line)

    def _get_stack_as_hit(self, msg, file=None, line=None):
        # we have something like <xml><thread id="12152656" stop_reason="111"><frame id="12453120" name="encode" ...
        if len(msg.thread.frame) == 0:
            frame = msg.thread.frame
        else:
            frame = msg.thread.frame[0]
        thread_id = msg.thread['id']
        frame_id = frame['id']
        suspend_type = msg.thread['suspend_type']
        name = frame['name']
        frame_line = int(frame['line'])
        frame_file = frame['file']

        if file is not None:
            assert frame_file.endswith(file), 'Expected hit to be in file %s, was: %s' % (file, frame_file)

        if line is not None:
            assert line == frame_line, 'Expected hit to be in line %s, was: %s' % (line, frame_line)

        self.log.append('End(1): wait_for_breakpoint_hit: %s' % (msg.original_xml,))

        return Hit(
            thread_id=thread_id, frame_id=frame_id, line=frame_line, suspend_type=suspend_type, name=name, file=frame_file)

    def wait_for_get_next_statement_targets(self):
        last = ''
        while not '<xml><line>' in last:
            last = self.get_next_message('wait_for_get_next_statement_targets')

        matches = re.finditer(r"(<line>([0-9]*)<\/line>)", last, re.IGNORECASE)
        lines = []
        for _, match in enumerate(matches):
            try:
                lines.append(int(match.group(2)))
            except ValueError:
                pass
        return set(lines)

    def wait_for_custom_operation(self, expected):
        # wait for custom operation response, the response is double encoded
        expected_encoded = quote(quote_plus(expected))
        last = ''
        while not expected_encoded in last:
            last = self.get_next_message('wait_for_custom_operation. Expected (encoded): %s' % (expected_encoded,))

        return True

    def _is_var_in_last(self, expected, last):
        if expected in last:
            return True

        last = unquote_plus(last)
        if expected in last:
            return True

        # We actually quote 2 times on the backend...
        last = unquote_plus(last)
        if expected in last:
            return True

        return False

    def wait_for_multiple_vars(self, expected_vars):
        if not isinstance(expected_vars, (list, tuple)):
            expected_vars = [expected_vars]

        all_found = []
        ignored = []

        while True:
            try:
                last = self.get_next_message('wait_for_multiple_vars: %s' % (expected_vars,))
            except:
                missing = []
                for v in expected_vars:
                    if v not in all_found:
                        missing.append(v)
                raise ValueError('Not Found:\n%s\nNot found messages: %s\nFound messages: %s\nExpected messages: %s\nIgnored messages:\n%s' % (
                    '\n'.join(str(x) for x in missing), len(missing), len(all_found), len(expected_vars), '\n'.join(str(x) for x in ignored)))

            was_message_used = False
            new_expected = []
            for expected in expected_vars:
                found_expected = False
                if isinstance(expected, (tuple, list)):
                    for e in expected:
                        if self._is_var_in_last(e, last):
                            was_message_used = True
                            found_expected = True
                            all_found.append(expected)
                            break
                else:
                    if self._is_var_in_last(expected, last):
                        was_message_used = True
                        found_expected = True
                        all_found.append(expected)

                if not found_expected:
                    new_expected.append(expected)

            expected_vars = new_expected

            if not expected_vars:
                return True

            if not was_message_used:
                ignored.append(last)

    wait_for_var = wait_for_multiple_vars
    wait_for_vars = wait_for_multiple_vars
    wait_for_evaluation = wait_for_multiple_vars

    def write_make_initial_run(self):
        self.write("101\t%s\t" % self.next_seq())
        self.log.append('write_make_initial_run')

    def write_version(self):
        from _pydevd_bundle.pydevd_constants import IS_WINDOWS
        self.write("%s\t%s\t1.0\t%s\tID" % (CMD_VERSION, self.next_seq(), 'WINDOWS' if IS_WINDOWS else 'UNIX'))

    def get_main_filename(self):
        return self.TEST_FILE

    def write_show_return_vars(self, show=1):
        self.write("%s\t%s\tCMD_SHOW_RETURN_VALUES\t%s" % (CMD_SHOW_RETURN_VALUES, self.next_seq(), show))

    def write_add_breakpoint(self, line, func='None', filename=None, hit_condition=None, is_logpoint=False, suspend_policy=None, condition=None):
        '''
        :param line: starts at 1
        :param func: if None, may hit in any context, empty string only top level, otherwise must be method name.
        '''
        if filename is None:
            filename = self.get_main_filename()
        breakpoint_id = self.next_breakpoint_id()
        if hit_condition is None and not is_logpoint and suspend_policy is None and condition is None:
            # Format kept for backward compatibility tests
            self.write("%s\t%s\t%s\t%s\t%s\t%s\t%s\tNone\tNone" % (
                CMD_SET_BREAK, self.next_seq(), breakpoint_id, 'python-line', filename, line, func))
        else:
            # Format: breakpoint_id, type, file, line, func_name, condition, expression, hit_condition, is_logpoint, suspend_policy
            self.write("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\tNone\t%s\t%s\t%s" % (
                CMD_SET_BREAK, self.next_seq(), breakpoint_id, 'python-line', filename, line, func, condition, hit_condition, is_logpoint, suspend_policy))
        self.log.append('write_add_breakpoint: %s line: %s func: %s' % (breakpoint_id, line, func))
        return breakpoint_id

    def write_multi_threads_single_notification(self, multi_threads_single_notification):
        self.write_json_config(dict(
            multi_threads_single_notification=multi_threads_single_notification,
        ))

    def write_suspend_on_breakpoint_exception(self, skip_suspend_on_breakpoint_exception, skip_print_breakpoint_exception):
        self.write_json_config(dict(
            skip_suspend_on_breakpoint_exception=skip_suspend_on_breakpoint_exception,
            skip_print_breakpoint_exception=skip_print_breakpoint_exception
        ))

    def write_json_config(self, config_dict):
        self.write("%s\t%s\t%s" % (CMD_PYDEVD_JSON_CONFIG, self.next_seq(),
            json.dumps(config_dict)
        ))

    def write_stop_on_start(self, stop=True):
        self.write("%s\t%s\t%s" % (CMD_STOP_ON_START, self.next_seq(), stop))

    def write_dump_threads(self):
        self.write("%s\t%s\t" % (CMD_THREAD_DUMP_TO_STDERR, self.next_seq()))

    def write_add_exception_breakpoint(self, exception):
        self.write("%s\t%s\t%s" % (CMD_ADD_EXCEPTION_BREAK, self.next_seq(), exception))
        self.log.append('write_add_exception_breakpoint: %s' % (exception,))

    def write_get_current_exception(self, thread_id):
        self.write("%s\t%s\t%s" % (CMD_GET_EXCEPTION_DETAILS, self.next_seq(), thread_id))

    def write_set_py_exception_globals(
            self,
            break_on_uncaught,
            break_on_caught,
            skip_on_exceptions_thrown_in_same_context,
            ignore_exceptions_thrown_in_lines_with_ignore_exception,
            ignore_libraries,
            exceptions=()
        ):
        # Only set the globals, others
        self.write("131\t%s\t%s" % (self.next_seq(), '%s;%s;%s;%s;%s;%s' % (
            'true' if break_on_uncaught else 'false',
            'true' if break_on_caught else 'false',
            'true' if skip_on_exceptions_thrown_in_same_context else 'false',
            'true' if ignore_exceptions_thrown_in_lines_with_ignore_exception else 'false',
            'true' if ignore_libraries else 'false',
            ';'.join(exceptions)
        )))
        self.log.append('write_set_py_exception_globals')

    def write_start_redirect(self):
        self.write("%s\t%s\t%s" % (CMD_REDIRECT_OUTPUT, self.next_seq(), 'STDERR STDOUT'))

    def write_set_project_roots(self, project_roots):
        self.write("%s\t%s\t%s" % (CMD_SET_PROJECT_ROOTS, self.next_seq(), '\t'.join(str(x) for x in project_roots)))

    def write_add_exception_breakpoint_with_policy(
            self, exception, notify_on_handled_exceptions, notify_on_unhandled_exceptions, ignore_libraries):
        self.write("%s\t%s\t%s" % (CMD_ADD_EXCEPTION_BREAK, self.next_seq(), '\t'.join(str(x) for x in [
            exception, notify_on_handled_exceptions, notify_on_unhandled_exceptions, ignore_libraries])))
        self.log.append('write_add_exception_breakpoint: %s' % (exception,))

    def write_remove_breakpoint(self, breakpoint_id):
        self.write("%s\t%s\t%s\t%s\t%s" % (
            CMD_REMOVE_BREAK, self.next_seq(), 'python-line', self.get_main_filename(), breakpoint_id))

    def write_change_variable(self, thread_id, frame_id, varname, value):
        self.write("%s\t%s\t%s\t%s\t%s\t%s\t%s" % (
            CMD_CHANGE_VARIABLE, self.next_seq(), thread_id, frame_id, 'FRAME', varname, value))

    def write_get_frame(self, thread_id, frame_id):
        self.write("%s\t%s\t%s\t%s\tFRAME" % (CMD_GET_FRAME, self.next_seq(), thread_id, frame_id))
        self.log.append('write_get_frame')

    def write_get_variable(self, thread_id, frame_id, var_attrs):
        self.write("%s\t%s\t%s\t%s\tFRAME\t%s" % (CMD_GET_VARIABLE, self.next_seq(), thread_id, frame_id, var_attrs))

    def write_step_over(self, thread_id):
        self.write("%s\t%s\t%s" % (CMD_STEP_OVER, self.next_seq(), thread_id,))

    def write_step_in(self, thread_id):
        self.write("%s\t%s\t%s" % (CMD_STEP_INTO, self.next_seq(), thread_id,))

    def write_step_return(self, thread_id):
        self.write("%s\t%s\t%s" % (CMD_STEP_RETURN, self.next_seq(), thread_id,))

    def write_suspend_thread(self, thread_id):
        self.write("%s\t%s\t%s" % (CMD_THREAD_SUSPEND, self.next_seq(), thread_id,))

    def write_run_thread(self, thread_id):
        self.log.append('write_run_thread')
        self.write("%s\t%s\t%s" % (CMD_THREAD_RUN, self.next_seq(), thread_id,))

    def write_get_thread_stack(self, thread_id):
        self.log.append('write_get_thread_stack')
        self.write("%s\t%s\t%s" % (CMD_GET_THREAD_STACK, self.next_seq(), thread_id,))

    def write_load_source(self, filename):
        self.log.append('write_load_source')
        self.write("%s\t%s\t%s" % (CMD_LOAD_SOURCE, self.next_seq(), filename,))

    def write_kill_thread(self, thread_id):
        self.write("%s\t%s\t%s" % (CMD_THREAD_KILL, self.next_seq(), thread_id,))

    def write_set_next_statement(self, thread_id, line, func_name):
        self.write("%s\t%s\t%s\t%s\t%s" % (CMD_SET_NEXT_STATEMENT, self.next_seq(), thread_id, line, func_name,))

    def write_debug_console_expression(self, locator):
        self.write("%s\t%s\t%s" % (CMD_EVALUATE_CONSOLE_EXPRESSION, self.next_seq(), locator))

    def write_custom_operation(self, locator, style, codeOrFile, operation_fn_name):
        self.write("%s\t%s\t%s||%s\t%s\t%s" % (
            CMD_RUN_CUSTOM_OPERATION, self.next_seq(), locator, style, codeOrFile, operation_fn_name))

    def write_evaluate_expression(self, locator, expression):
        self.write("%s\t%s\t%s\t%s\t1" % (CMD_EVALUATE_EXPRESSION, self.next_seq(), locator, expression))

    def write_enable_dont_trace(self, enable):
        if enable:
            enable = 'true'
        else:
            enable = 'false'
        self.write("%s\t%s\t%s" % (CMD_ENABLE_DONT_TRACE, self.next_seq(), enable))

    def write_get_next_statement_targets(self, thread_id, frame_id):
        self.write("201\t%s\t%s\t%s" % (self.next_seq(), thread_id, frame_id))
        self.log.append('write_get_next_statement_targets')

    def write_list_threads(self):
        seq = self.next_seq()
        self.write("%s\t%s\t" % (CMD_LIST_THREADS, seq))
        return seq

    def wait_for_list_threads(self, seq):
        return self.wait_for_message('502')

    def wait_for_get_thread_stack_message(self):
        return self.wait_for_message(CMD_GET_THREAD_STACK)

    def wait_for_json_message(self, accept_message, unquote_msg=True, timeout=None):
        last = self.wait_for_message(accept_message, unquote_msg, expect_xml=False, timeout=timeout)
        json_msg = last.split('\t', 2)[-1]  # We have something as: CMD\tSEQ\tJSON
        if isinstance(json_msg, bytes):
            json_msg = json_msg.decode('utf-8')
        try:
            return json.loads(json_msg)
        except:
            traceback.print_exc()
            raise AssertionError('Unable to parse:\n%s\njson:\n%s' % (last, json_msg))

    def wait_for_message(self, accept_message, unquote_msg=True, expect_xml=True, timeout=None):
        if isinstance(accept_message, (str, int)):
            msg_starts_with = '%s\t' % (accept_message,)

            def accept_message(msg):
                return msg.startswith(msg_starts_with)

        import untangle
        from io import StringIO
        prev = None
        while True:
            last = self.get_next_message('wait_for_message', timeout=timeout)
            if unquote_msg:
                last = unquote_plus(unquote_plus(last))
            if accept_message(last):
                if expect_xml:
                    # Extract xml and return untangled.
                    try:
                        xml = last[last.index('<xml>'):]
                        if isinstance(xml, bytes):
                            xml = xml.decode('utf-8')
                        xml = untangle.parse(StringIO(xml))
                    except:
                        traceback.print_exc()
                        raise AssertionError('Unable to parse:\n%s\nxml:\n%s' % (last, xml))
                    ret = xml.xml
                    ret.original_xml = last
                    return ret
                else:
                    return last
            if prev != last:
                print('Ignored message: %r' % (last,))

            prev = last

    def get_frame_names(self, thread_id):
        self.write_get_thread_stack(thread_id)
        msg = self.wait_for_message(CMD_GET_THREAD_STACK)
        if msg.thread.frame:
            frame_names = [frame['name'] for frame in msg.thread.frame]
            return frame_names
        return [msg.thread.frame['name']]

    def wait_for_thread_join(self, main_thread_id):

        def condition():
            return self.get_frame_names(main_thread_id) in (
                ['wait', 'join', '<module>'],
                ['_wait_for_tstate_lock', 'join', '<module>']
            )

        def msg():
            return 'Found stack: %s' % (self.get_frame_names(main_thread_id),)

        wait_for_condition(condition, msg, timeout=5, sleep=.5)


class AbstractDispatcherThread(AbstractWriterThread):
    """For using with the ``--multiproc`` option. Allocates new writers and dispatches breakpoints to them."""
    def __init__(self):
        super(AbstractDispatcherThread, self).__init__()
        self.writers = []
        self._sequence = -1
        self._breakpoints = []

    def run(self):
        server_socket = _create_socket()
        server_socket.listen(1)
        self.port = server_socket.getsockname()[1]
        self.finished_initialization = True

        while True:
            self.sock, addr = server_socket.accept()
            new_writer = AbstractWriterThread()
            new_writer.TEST_FILE = self.TEST_FILE
            new_writer.start()
            wait_for_condition(lambda: hasattr(new_writer, 'port'))
            self.write('99\t-1\t%d' % new_writer.port)
            wait_for_condition(lambda: new_writer.finished_initialization)

            for args, kwargs in self._breakpoints:
                new_writer.write_add_breakpoint(*args, **kwargs)

            self.writers.append(new_writer)

    def write_add_breakpoint(self, *args, **kwargs):
        self._breakpoints.append((args, kwargs))


def _get_debugger_test_file(filename):
    try:
        rPath = os.path.realpath  # @UndefinedVariable
    except:
        # jython does not support os.path.realpath
        # realpath is a no-op on systems without islink support
        rPath = os.path.abspath

    ret = os.path.normcase(rPath(os.path.join(os.path.dirname(__file__), filename)))
    if not os.path.exists(ret):
        ret = os.path.join(os.path.dirname(ret), 'resources', os.path.basename(ret))
    if not os.path.exists(ret):
        raise AssertionError('Expected: %s to exist.' % (ret,))
    return ret


def get_free_port():
    from _pydev_bundle.pydev_localhost import get_socket_name
    return get_socket_name(close=True)[1]
