try:
    from urllib import quote, quote_plus, unquote_plus
except ImportError:
    from urllib.parse import quote, quote_plus, unquote_plus #@UnresolvedImport


import os
import socket
import subprocess
import sys
import threading
import time

from _pydev_bundle import pydev_localhost


IS_PY3K = sys.version_info[0] >= 3

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

CMD_VERSION = 501
CMD_RETURN = 502
CMD_ERROR = 901



# Always True (because otherwise when we do have an error, it's hard to diagnose).
SHOW_WRITES_AND_READS = True
SHOW_OTHER_DEBUG_INFO = True
SHOW_STDOUT = True


try:
    from thread import start_new_thread
except ImportError:
    from _thread import start_new_thread  # @UnresolvedImport

try:
    xrange
except:
    xrange = range


#=======================================================================================================================
# ReaderThread
#=======================================================================================================================
class ReaderThread(threading.Thread):

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
        
    def get_next_message(self, context_messag):
        try:
            msg = self._queue.get(block=True, timeout=15)
        except:
            raise AssertionError('No message was written in 15 seconds. Error message:\n%s' % (context_messag,))
        else:
            frame = sys._getframe().f_back
            frame_info = ' --  File "%s", line %s, in %s\n' % (frame.f_code.co_filename, frame.f_lineno, frame.f_code.co_name)
            frame_info += ' --  File "%s", line %s, in %s\n' % (frame.f_back.f_code.co_filename, frame.f_back.f_lineno, frame.f_back.f_code.co_name)
            frame = None
            sys.stdout.write('Message returned in get_next_message(): %s --  ctx: %s, returned to:\n%s\n' % (msg, context_messag, frame_info))
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
                    i = buf.index('\n')+1
                    last_received = buf[:i]
                    buf = buf[i:]

                    if SHOW_WRITES_AND_READS:
                        print('Test Reader Thread Received %s' % (last_received, ))
                        
                    self._queue.put(last_received)
        except:
            pass  # ok, finished it
        finally:
            del self.all_received[:]

    def do_kill(self):
        self._kill = True
        if hasattr(self, 'sock'):
            self.sock.close()


class DebuggerRunner(object):

    def get_command_line(self):
        '''
        Returns the base command line (i.e.: ['python.exe', '-u'])
        '''
        raise NotImplementedError

    def add_command_line_args(self, args):
        writer_thread = self.writer_thread
        port = int(writer_thread.port)

        localhost = pydev_localhost.get_localhost()
        ret = args + [
            writer_thread.get_pydevd_file(),
            '--DEBUG_RECORD_SOCKET_READS',
            '--qt-support',
            '--client',
            localhost,
            '--port',
            str(port),
        ]
        
        if writer_thread.IS_MODULE:
            ret += ['--module']
        
        ret = ret + ['--file'] + writer_thread.get_command_line_args()
        return ret

    def check_case(self, writer_thread_class):
        writer_thread = writer_thread_class()
        try:
            writer_thread.start()
            for _i in xrange(40000):
                if hasattr(writer_thread, 'port'):
                    break
                time.sleep(.01)
            self.writer_thread = writer_thread

            args = self.get_command_line()

            args = self.add_command_line_args(args)

            if SHOW_OTHER_DEBUG_INFO:
                print('executing', ' '.join(args))

            ret = self.run_process(args, writer_thread)
        finally:
            writer_thread.do_kill()
            writer_thread.log = []
            
        stdout = ret['stdout']
        stderr = ret['stderr']
        writer_thread.additional_output_checks(''.join(stdout), ''.join(stderr))
        return ret

    def create_process(self, args, writer_thread):
        process = subprocess.Popen(
            args,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            cwd=writer_thread.get_cwd() if writer_thread is not None else '.',
            env=writer_thread.get_environ() if writer_thread is not None else None,
        )
        return process

    def run_process(self, args, writer_thread):
        process = self.create_process(args, writer_thread)
        stdout = []
        stderr = []
        finish = [False]

        try:
            def read(stream, buffer):
                for line in stream.readlines():
                    if finish[0]:
                        return
                    if IS_PY3K:
                        line = line.decode('utf-8')

                    if SHOW_STDOUT:
                        sys.stdout.write('stdout: %s' % (line,))
                    buffer.append(line)

            start_new_thread(read, (process.stdout, stdout))


            if SHOW_OTHER_DEBUG_INFO:
                print('Both processes started')

            # polls can fail (because the process may finish and the thread still not -- so, we give it some more chances to
            # finish successfully).
            initial_time = time.time()
            shown_intermediate = False
            while True:
                if process.poll() is not None:
                    break
                else:
                    if writer_thread is not None:
                        if not writer_thread.isAlive():
                            if writer_thread.FORCE_KILL_PROCESS_WHEN_FINISHED_OK:
                                process.kill()
                                continue

                            if not shown_intermediate and (time.time() - initial_time > 10):
                                print('Warning: writer thread exited and process still did not (%.2fs seconds elapsed).' % (time.time() - initial_time,))
                                shown_intermediate = True
                                
                            if time.time() - initial_time > 20:
                                process.kill()
                                time.sleep(.2)
                                self.fail_with_message(
                                    "The other process should've exited but still didn't (%.2fs seconds timeout for process to exit)." % (time.time() - initial_time,),
                                    stdout, stderr, writer_thread
                                )
                time.sleep(.2)


            if writer_thread is not None:
                if not writer_thread.FORCE_KILL_PROCESS_WHEN_FINISHED_OK:
                    poll = process.poll()
                    if poll < 0:
                        self.fail_with_message(
                            "The other process exited with error code: " + str(poll), stdout, stderr, writer_thread)


                    if stdout is None:
                        self.fail_with_message(
                            "The other process may still be running -- and didn't give any output.", stdout, stderr, writer_thread)

                    check = 0
                    while 'TEST SUCEEDED' not in ''.join(stdout):
                        check += 1
                        if check == 50:
                            self.fail_with_message("TEST SUCEEDED not found in stdout.", stdout, stderr, writer_thread)
                        time.sleep(.1)

                for _i in xrange(100):
                    if not writer_thread.finished_ok:
                        time.sleep(.1)

                if not writer_thread.finished_ok:
                    self.fail_with_message(
                        "The thread that was doing the tests didn't finish successfully.", stdout, stderr, writer_thread)
        finally:
            finish[0] = True

        return {'stdout':stdout, 'stderr':stderr}

    def fail_with_message(self, msg, stdout, stderr, writerThread):
        raise AssertionError(msg+
            "\n\n===========================\nStdout: \n"+''.join(stdout)+
            "\n\n===========================\nStderr:"+''.join(stderr)+
            "\n\n===========================\nLog:\n"+'\n'.join(getattr(writerThread, 'log', [])))



#=======================================================================================================================
# AbstractWriterThread
#=======================================================================================================================
class AbstractWriterThread(threading.Thread):

    FORCE_KILL_PROCESS_WHEN_FINISHED_OK = False
    IS_MODULE = False

    def __init__(self):
        threading.Thread.__init__(self)
        self.setDaemon(True)
        self.finished_ok = False
        self._next_breakpoint_id = 0
        self.log = []
        
    def additional_output_checks(self, stdout, stderr):
        pass

    def get_environ(self):
        return None

    def get_pydevd_file(self):
        dirname = os.path.dirname(__file__)
        dirname = os.path.dirname(dirname)
        return os.path.abspath(os.path.join(dirname, 'pydevd.py'))

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
        self.log.append('write: %s' % (s,))

        if SHOW_WRITES_AND_READS:
            print('Test Writer Thread Written %s' % (s,))
        msg = s + '\n'
        if IS_PY3K:
            msg = msg.encode('utf-8')
        self.sock.send(msg)

    def start_socket(self, port=None):
        from _pydev_bundle.pydev_localhost import get_socket_name
        if SHOW_WRITES_AND_READS:
            print('start_socket')

        if port is None:
            socket_name = get_socket_name(close=True)
        else:
            socket_name = (pydev_localhost.get_localhost(), port)
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(socket_name)
        self.port = socket_name[1]
        s.listen(1)
        if SHOW_WRITES_AND_READS:
            print('Waiting in socket.accept()')
        self.server_socket = s
        newSock, addr = s.accept()
        if SHOW_WRITES_AND_READS:
            print('Test Writer Thread Socket:', newSock, addr)

        reader_thread = self.reader_thread = ReaderThread(newSock)
        reader_thread.start()
        self.sock = newSock

        self._sequence = -1
        # initial command is always the version
        self.write_version()
        self.log.append('start_socket')

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
            last = self.reader_thread.get_next_message('wait_for_new_thread')

        # we have something like <xml><thread name="MainThread" id="12103472" /></xml>
        splitted = last.split('"')
        thread_id = splitted[3]
        return thread_id

    def wait_for_breakpoint_hit(self, *args, **kwargs):
        return self.wait_for_breakpoint_hit_with_suspend_type(*args, **kwargs)[:-1]

    def wait_for_breakpoint_hit_with_suspend_type(self, reason='111', get_line=False, get_name=False):
        '''
            108 is over
            109 is return
            111 is breakpoint
        '''
        self.log.append('Start: wait_for_breakpoint_hit')
        # wait for hit breakpoint
        last = ''
        while not ('stop_reason="%s"' % reason) in last:
            last = self.reader_thread.get_next_message('wait_for_breakpoint_hit. reason=%s' % (reason,))

        # we have something like <xml><thread id="12152656" stop_reason="111"><frame id="12453120" name="encode" ...
        splitted = last.split('"')
        suspend_type = splitted[7]
        thread_id = splitted[1]
        frameId = splitted[9]
        name = splitted[11]
        if get_line:
            self.log.append('End(0): wait_for_breakpoint_hit: %s' % (last,))
            try:
                if not get_name:
                    return thread_id, frameId, int(splitted[15]), suspend_type
                else:
                    return thread_id, frameId, int(splitted[15]), name, suspend_type
            except:
                raise AssertionError('Error with: %s, %s, %s.\nLast: %s.\n\nAll: %s\n\nSplitted: %s' % (
                    thread_id, frameId, splitted[13], last, '\n'.join(self.reader_thread.all_received), splitted))

        self.log.append('End(1): wait_for_breakpoint_hit: %s' % (last,))
        if not get_name:
            return thread_id, frameId, suspend_type
        else:
            return thread_id, frameId, name, suspend_type

    def wait_for_custom_operation(self, expected):
        # wait for custom operation response, the response is double encoded
        expected_encoded = quote(quote_plus(expected))
        last = ''
        while not expected_encoded in last:
            last = self.reader_thread.get_next_message('wait_for_custom_operation. Expected (encoded): %s' % (expected_encoded,))

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
        while True:
            try:
                last = self.reader_thread.get_next_message('wait_for_multiple_vars: %s' % (expected_vars,))
            except:
                missing = []
                for v in expected_vars:
                    if v not in all_found:
                        missing.append(v)
                raise ValueError('Not Found:\n%s\nNot found messages: %s\nFound messages: %s\nExpected messages: %s' % (
                    '\n'.join(missing), len(missing), len(all_found), len(expected_vars)))
            found = 0
            for expected in expected_vars:
                if isinstance(expected, (tuple, list)):
                    for e in expected:
                        if self._is_var_in_last(e, last):
                            all_found.append(expected)
                            found += 1
                            break
                else:
                    if self._is_var_in_last(expected, last):
                        all_found.append(expected)
                        found += 1
                        
            if found == len(expected_vars):
                return True
                        
    wait_for_var = wait_for_multiple_vars
    wait_for_vars = wait_for_multiple_vars
    wait_for_evaluation = wait_for_multiple_vars

    def write_make_initial_run(self):
        self.write("101\t%s\t" % self.next_seq())
        self.log.append('write_make_initial_run')

    def write_version(self):
        self.write("501\t%s\t1.0\tWINDOWS\tID" % self.next_seq())
        
    def get_main_filename(self):
        return self.TEST_FILE

    def write_add_breakpoint(self, line, func):
        '''
            @param line: starts at 1
        '''
        breakpoint_id = self.next_breakpoint_id()
        self.write("111\t%s\t%s\t%s\t%s\t%s\t%s\tNone\tNone" % (self.next_seq(), breakpoint_id, 'python-line', self.get_main_filename(), line, func))
        self.log.append('write_add_breakpoint: %s line: %s func: %s' % (breakpoint_id, line, func))
        return breakpoint_id

    def write_add_exception_breakpoint(self, exception):
        self.write("122\t%s\t%s" % (self.next_seq(), exception))
        self.log.append('write_add_exception_breakpoint: %s' % (exception,))

    def write_add_exception_breakpoint_with_policy(self, exception, notify_always, notify_on_terminate, ignore_libraries):
        self.write("122\t%s\t%s" % (self.next_seq(), '\t'.join([exception, notify_always, notify_on_terminate, ignore_libraries])))
        self.log.append('write_add_exception_breakpoint: %s' % (exception,))

    def write_remove_breakpoint(self, breakpoint_id):
        self.write("112\t%s\t%s\t%s\t%s" % (self.next_seq(), 'python-line', self.get_main_filename(), breakpoint_id))

    def write_change_variable(self, thread_id, frame_id, varname, value):
        self.write("117\t%s\t%s\t%s\t%s\t%s\t%s" % (self.next_seq(), thread_id, frame_id, 'FRAME', varname, value))

    def write_get_frame(self, thread_id, frameId):
        self.write("114\t%s\t%s\t%s\tFRAME" % (self.next_seq(), thread_id, frameId))
        self.log.append('write_get_frame')

    def write_get_variable(self, thread_id, frameId, var_attrs):
        self.write("110\t%s\t%s\t%s\tFRAME\t%s" % (self.next_seq(), thread_id, frameId, var_attrs))

    def write_step_over(self, thread_id):
        self.write("108\t%s\t%s" % (self.next_seq(), thread_id,))

    def write_step_in(self, thread_id):
        self.write("107\t%s\t%s" % (self.next_seq(), thread_id,))

    def write_step_return(self, thread_id):
        self.write("109\t%s\t%s" % (self.next_seq(), thread_id,))

    def write_suspend_thread(self, thread_id):
        self.write("105\t%s\t%s" % (self.next_seq(), thread_id,))

    def write_run_thread(self, thread_id):
        self.log.append('write_run_thread')
        self.write("106\t%s\t%s" % (self.next_seq(), thread_id,))

    def write_kill_thread(self, thread_id):
        self.write("104\t%s\t%s" % (self.next_seq(), thread_id,))

    def write_set_next_statement(self, thread_id, line, func_name):
        self.write("%s\t%s\t%s\t%s\t%s" % (CMD_SET_NEXT_STATEMENT, self.next_seq(), thread_id, line, func_name,))

    def write_debug_console_expression(self, locator):
        self.write("%s\t%s\t%s" % (CMD_EVALUATE_CONSOLE_EXPRESSION, self.next_seq(), locator))

    def write_custom_operation(self, locator, style, codeOrFile, operation_fn_name):
        self.write("%s\t%s\t%s||%s\t%s\t%s" % (CMD_RUN_CUSTOM_OPERATION, self.next_seq(), locator, style, codeOrFile, operation_fn_name))

    def write_evaluate_expression(self, locator, expression):
        self.write("113\t%s\t%s\t%s\t1" % (self.next_seq(), locator, expression))

    def write_enable_dont_trace(self, enable):
        if enable:
            enable = 'true'
        else:
            enable = 'false'
        self.write("%s\t%s\t%s" % (CMD_ENABLE_DONT_TRACE, self.next_seq(), enable))

def _get_debugger_test_file(filename):
    try:
        rPath = os.path.realpath  # @UndefinedVariable
    except:
        # jython does not support os.path.realpath
        # realpath is a no-op on systems without islink support
        rPath = os.path.abspath

    return os.path.normcase(rPath(os.path.join(os.path.dirname(__file__), filename)))

def get_free_port():
    from _pydev_bundle.pydev_localhost import get_socket_name
    return get_socket_name(close=True)[1]
