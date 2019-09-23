''' pydevd - a debugging daemon
This is the daemon you launch for python remote debugging.

Protocol:
each command has a format:
    id\tsequence-num\ttext
    id: protocol command number
    sequence-num: each request has a sequence number. Sequence numbers
    originating at the debugger are odd, sequence numbers originating
    at the daemon are even. Every response uses the same sequence number
    as the request.
    payload: it is protocol dependent. When response is a complex structure, it
    is returned as XML. Each attribute value is urlencoded, and then the whole
    payload is urlencoded again to prevent stray characters corrupting protocol/xml encodings

    Commands:

    NUMBER   NAME                     FROM*     ARGUMENTS                     RESPONSE      NOTE
100 series: program execution
    101      RUN                      JAVA      -                             -
    102      LIST_THREADS             JAVA                                    RETURN with XML listing of all threads
    103      THREAD_CREATE            PYDB      -                             XML with thread information
    104      THREAD_KILL              JAVA      id (or * to exit)             kills the thread
                                      PYDB      id                            nofies JAVA that thread was killed
    105      THREAD_SUSPEND           JAVA      XML of the stack,             suspends the thread
                                                reason for suspension
                                      PYDB      id                            notifies JAVA that thread was suspended

    106      CMD_THREAD_RUN           JAVA      id                            resume the thread
                                      PYDB      id \t reason                  notifies JAVA that thread was resumed

    107      STEP_INTO                JAVA      thread_id
    108      STEP_OVER                JAVA      thread_id
    109      STEP_RETURN              JAVA      thread_id

    110      GET_VARIABLE             JAVA      thread_id \t frame_id \t      GET_VARIABLE with XML of var content
                                                FRAME|GLOBAL \t attributes*

    111      SET_BREAK                JAVA      file/line of the breakpoint
    112      REMOVE_BREAK             JAVA      file/line of the return
    113      CMD_EVALUATE_EXPRESSION  JAVA      expression                    result of evaluating the expression
    114      CMD_GET_FRAME            JAVA                                    request for frame contents
    115      CMD_EXEC_EXPRESSION      JAVA
    116      CMD_WRITE_TO_CONSOLE     PYDB
    117      CMD_CHANGE_VARIABLE
    118      CMD_RUN_TO_LINE
    119      CMD_RELOAD_CODE
    120      CMD_GET_COMPLETIONS      JAVA

    200      CMD_REDIRECT_OUTPUT      JAVA      streams to redirect as string -
                                                'STDOUT' (redirect only STDOUT)
                                                'STDERR' (redirect only STDERR)
                                                'STDOUT STDERR' (redirect both streams)

500 series diagnostics/ok
    501      VERSION                  either      Version string (1.0)        Currently just used at startup
    502      RETURN                   either      Depends on caller    -

900 series: errors
    901      ERROR                    either      -                           This is reserved for unexpected errors.

    * JAVA - remote debugger, the java end
    * PYDB - pydevd, the python end
'''

import itertools

from _pydev_bundle.pydev_imports import _queue
from _pydev_imps._pydev_saved_modules import thread
from _pydev_imps._pydev_saved_modules import threading
from _pydev_imps._pydev_saved_modules import time
from _pydev_imps._pydev_saved_modules import socket
from socket import socket, AF_INET, SOCK_STREAM, SHUT_RD, SHUT_WR, SOL_SOCKET, SO_REUSEADDR, SHUT_RDWR, timeout
from _pydevd_bundle.pydevd_constants import DebugInfoHolder, get_thread_id, IS_JYTHON, IS_PY2, IS_PY3K, \
    IS_PY36_OR_GREATER, STATE_RUN, dict_keys, ASYNC_EVAL_TIMEOUT_SEC, IS_IRONPYTHON, GlobalDebuggerHolder, \
    get_global_debugger, GetGlobalDebugger, set_global_debugger # Keep for backward compatibility @UnusedImport
from _pydev_bundle.pydev_override import overrides
import json
import weakref

try:
    from urllib import quote_plus, unquote, unquote_plus
except:
    from urllib.parse import quote_plus, unquote, unquote_plus  #@Reimport @UnresolvedImport

if IS_IRONPYTHON:
    # redefine `unquote` for IronPython, since we use it only for logging messages, but it leads to SOF with IronPython
    def unquote(s):
        return s

from _pydevd_bundle import pydevd_console_integration
from _pydevd_bundle import pydevd_vars
import pydevd_tracing
from _pydevd_bundle import pydevd_xml
from _pydevd_bundle import pydevd_vm_type
from pydevd_file_utils import get_abs_path_real_path_and_base_from_frame, norm_file_to_client, is_real_file
import pydevd_file_utils
import os
import sys
import traceback
from _pydevd_bundle.pydevd_utils import quote_smart as quote, compare_object_attrs_key, to_string, \
    get_non_pydevd_threads
from _pydev_bundle.pydev_is_thread_alive import is_thread_alive
from _pydev_bundle import pydev_log
from _pydev_bundle import _pydev_completer

from pydevd_tracing import get_exception_traceback_str
from _pydevd_bundle import pydevd_console
from _pydev_bundle.pydev_monkey import disable_trace_thread_modules, enable_trace_thread_modules

try:
    import cStringIO as StringIO #may not always be available @UnusedImport
except:
    try:
        import StringIO #@Reimport
    except:
        import io as StringIO

from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE, PYDEV_FILE
get_file_type = DONT_TRACE.get


# CMD_XXX constants imported for backward compatibility
from _pydevd_bundle.pydevd_comm_constants import (
    ID_TO_MEANING, CMD_RUN, CMD_LIST_THREADS, CMD_THREAD_CREATE, CMD_THREAD_KILL,
    CMD_THREAD_SUSPEND, CMD_THREAD_RUN, CMD_STEP_INTO, CMD_STEP_OVER, CMD_STEP_RETURN, CMD_GET_VARIABLE,
    CMD_SET_BREAK, CMD_REMOVE_BREAK, CMD_EVALUATE_EXPRESSION, CMD_GET_FRAME,
    CMD_EXEC_EXPRESSION, CMD_WRITE_TO_CONSOLE, CMD_CHANGE_VARIABLE, CMD_RUN_TO_LINE,
    CMD_RELOAD_CODE, CMD_GET_COMPLETIONS, CMD_CONSOLE_EXEC, CMD_ADD_EXCEPTION_BREAK,
    CMD_REMOVE_EXCEPTION_BREAK, CMD_LOAD_SOURCE, CMD_ADD_DJANGO_EXCEPTION_BREAK,
    CMD_REMOVE_DJANGO_EXCEPTION_BREAK, CMD_SET_NEXT_STATEMENT, CMD_SMART_STEP_INTO,
    CMD_EXIT, CMD_SIGNATURE_CALL_TRACE, CMD_SET_PY_EXCEPTION, CMD_GET_FILE_CONTENTS,
    CMD_SET_PROPERTY_TRACE, CMD_EVALUATE_CONSOLE_EXPRESSION, CMD_RUN_CUSTOM_OPERATION,
    CMD_GET_BREAKPOINT_EXCEPTION, CMD_STEP_CAUGHT_EXCEPTION, CMD_SEND_CURR_EXCEPTION_TRACE,
    CMD_SEND_CURR_EXCEPTION_TRACE_PROCEEDED, CMD_IGNORE_THROWN_EXCEPTION_AT, CMD_ENABLE_DONT_TRACE,
    CMD_SHOW_CONSOLE, CMD_GET_ARRAY, CMD_STEP_INTO_MY_CODE, CMD_GET_CONCURRENCY_EVENT,
    CMD_SHOW_RETURN_VALUES, CMD_INPUT_REQUESTED, CMD_GET_DESCRIPTION, CMD_PROCESS_CREATED,
    CMD_SHOW_CYTHON_WARNING, CMD_LOAD_FULL_VALUE, CMD_GET_THREAD_STACK, CMD_THREAD_DUMP_TO_STDERR,
    CMD_STOP_ON_START, CMD_GET_EXCEPTION_DETAILS, CMD_PROCESS_CREATED_MSG_RECEIVED, CMD_PYDEVD_JSON_CONFIG,
    CMD_THREAD_SUSPEND_SINGLE_NOTIFICATION, CMD_THREAD_RESUME_SINGLE_NOTIFICATION,
    CMD_REDIRECT_OUTPUT, CMD_GET_NEXT_STATEMENT_TARGETS, CMD_SET_PROJECT_ROOTS, CMD_VERSION,
    CMD_RETURN, CMD_SET_PROTOCOL, CMD_ERROR,)
MAX_IO_MSG_SIZE = 1000  #if the io is too big, we'll not send all (could make the debugger too non-responsive)
#this number can be changed if there's need to do so

VERSION_STRING = "@@BUILD_NUMBER@@"

from _pydev_bundle._pydev_filesystem_encoding import getfilesystemencoding
file_system_encoding = getfilesystemencoding()
filesystem_encoding_is_utf8 = file_system_encoding.lower() in ('utf-8', 'utf_8', 'utf8')


class CommunicationRole(object):
    """The class that contains the constants of roles that `PyDB` can play in
    the communication with the IDE.
    """
    CLIENT = 0
    SERVER = 1


#--------------------------------------------------------------------------------------------------- UTILITIES

#=======================================================================================================================
# pydevd_log
#=======================================================================================================================
def pydevd_log(level, *args):
    """ levels are:
        0 most serious warnings/errors
        1 warnings/significant events
        2 informational trace
    """
    if level <= DebugInfoHolder.DEBUG_TRACE_LEVEL:
        #yes, we can have errors printing if the console of the program has been finished (and we're still trying to print something)
        try:
            sys.stderr.write('%s\n' % (args,))
        except:
            pass


#------------------------------------------------------------------- ACTUAL COMM

#=======================================================================================================================
# PyDBDaemonThread
#=======================================================================================================================
class PyDBDaemonThread(threading.Thread):
    created_pydb_daemon_threads = {}

    def __init__(self, target_and_args=None):
        '''
        :param target_and_args:
            tuple(func, args, kwargs) if this should be a function and args to run.
            -- Note: use through run_as_pydevd_daemon_thread().
        '''
        threading.Thread.__init__(self)
        self.killReceived = False
        mark_as_pydevd_daemon_thread(self)
        self._target_and_args = target_and_args

    def run(self):
        created_pydb_daemon = self.created_pydb_daemon_threads
        created_pydb_daemon[self] = 1
        try:
            try:
                if IS_JYTHON and not isinstance(threading.currentThread(), threading._MainThread):
                    # we shouldn't update sys.modules for the main thread, cause it leads to the second importing 'threading'
                    # module, and the new instance of main thread is created
                    import org.python.core as PyCore #@UnresolvedImport
                    ss = PyCore.PySystemState()
                    # Note: Py.setSystemState() affects only the current thread.
                    PyCore.Py.setSystemState(ss)

                self._stop_trace()
                self._on_run()
            except:
                if sys is not None and traceback is not None:
                    traceback.print_exc()
        finally:
            del created_pydb_daemon[self]

    def _on_run(self):
        if self._target_and_args is not None:
            target, args, kwargs = self._target_and_args
            target(*args, **kwargs)
        else:
            raise NotImplementedError('Should be reimplemented by: %s' % self.__class__)

    def do_kill_pydev_thread(self):
        self.killReceived = True

    def _stop_trace(self):
        if self.pydev_do_not_trace:
            pydevd_tracing.SetTrace(None)  # no debugging on this thread


def mark_as_pydevd_daemon_thread(thread):
    thread.pydev_do_not_trace = True
    thread.is_pydev_daemon_thread = True
    thread.daemon = True


def run_as_pydevd_daemon_thread(func, *args, **kwargs):
    '''
    Runs a function as a pydevd daemon thread (without any tracing in place).
    '''
    t = PyDBDaemonThread(target_and_args=(func, args, kwargs))
    t.name = '%s (pydevd daemon thread)' % (func.__name__,)
    t.start()
    return t


#=======================================================================================================================
# ReaderThread
#=======================================================================================================================
class ReaderThread(PyDBDaemonThread):
    """ reader thread reads and dispatches commands in an infinite loop """

    def __init__(self, sock):
        PyDBDaemonThread.__init__(self)
        self.sock = sock
        self.setName("pydevd.Reader")
        from _pydevd_bundle.pydevd_process_net_command import process_net_command
        self.process_net_command = process_net_command
        self.global_debugger_holder = GlobalDebuggerHolder



    def do_kill_pydev_thread(self):
        #We must close the socket so that it doesn't stay halted there.
        self.killReceived = True
        try:
            self.sock.shutdown(SHUT_RD) #shutdown the socket for read
        except:
            #just ignore that
            pass

    @overrides(PyDBDaemonThread._on_run)
    def _on_run(self):
        read_buffer = ""
        try:

            while not self.killReceived:
                try:
                    r = self.sock.recv(1024)
                except:
                    if not self.killReceived:
                        traceback.print_exc()
                        self.handle_except()
                    return #Finished communication.

                #Note: the java backend is always expected to pass utf-8 encoded strings. We now work with unicode
                #internally and thus, we may need to convert to the actual encoding where needed (i.e.: filenames
                #on python 2 may need to be converted to the filesystem encoding).
                if hasattr(r, 'decode'):
                    r = r.decode('utf-8')

                read_buffer += r
                if DebugInfoHolder.DEBUG_RECORD_SOCKET_READS:
                    sys.stderr.write(u'debugger: received >>%s<<\n' % (read_buffer,))
                    sys.stderr.flush()

                if len(read_buffer) == 0:
                    self.handle_except()
                    break
                while read_buffer.find(u'\n') != -1:
                    command, read_buffer = read_buffer.split(u'\n', 1)

                    args = command.split(u'\t', 2)
                    try:
                        cmd_id = int(args[0])
                        pydev_log.debug('Received command: %s %s\n' % (ID_TO_MEANING.get(str(cmd_id), '???'), command,))
                        self.process_command(cmd_id, int(args[1]), args[2])
                    except:
                        traceback.print_exc()
                        sys.stderr.write("Can't process net command: %s\n" % command)
                        sys.stderr.flush()

        except:
            traceback.print_exc()
            self.handle_except()


    def handle_except(self):
        self.global_debugger_holder.global_dbg.finish_debugging_session()

    def process_command(self, cmd_id, seq, text):
        self.process_net_command(self.global_debugger_holder.global_dbg, cmd_id, seq, text)


#----------------------------------------------------------------------------------- SOCKET UTILITIES - WRITER
#=======================================================================================================================
# WriterThread
#=======================================================================================================================
class WriterThread(PyDBDaemonThread):
    """ writer thread writes out the commands in an infinite loop """
    def __init__(self, sock):
        PyDBDaemonThread.__init__(self)
        self.sock = sock
        self.setName("pydevd.Writer")
        self.cmdQueue = _queue.Queue()
        if pydevd_vm_type.get_vm_type() == 'python':
            self.timeout = 0
        else:
            self.timeout = 0.1

    def add_command(self, cmd):
        """ cmd is NetCommand """
        if not self.killReceived: #we don't take new data after everybody die
            self.cmdQueue.put(cmd)

    @overrides(PyDBDaemonThread._on_run)
    def _on_run(self):
        """ just loop and write responses """

        try:
            while True:
                try:
                    try:
                        cmd = self.cmdQueue.get(1, 0.1)
                    except _queue.Empty:
                        if self.killReceived:
                            try:
                                self.sock.shutdown(SHUT_WR)
                                self.sock.close()
                            except:
                                pass

                            return #break if queue is empty and killReceived
                        else:
                            continue
                except:
                    #pydevd_log(0, 'Finishing debug communication...(1)')
                    #when liberating the thread here, we could have errors because we were shutting down
                    #but the thread was still not liberated
                    return
                cmd.send(self.sock)

                if cmd.id == CMD_EXIT:
                    break
                if time is None:
                    break #interpreter shutdown
                time.sleep(self.timeout)
        except Exception:
            GlobalDebuggerHolder.global_dbg.finish_debugging_session()
            if DebugInfoHolder.DEBUG_TRACE_LEVEL >= 0:
                traceback.print_exc()

    def empty(self):
        return self.cmdQueue.empty()



#--------------------------------------------------- CREATING THE SOCKET THREADS

#=======================================================================================================================
# start_server
#=======================================================================================================================
def start_server(port):
    """ binds to a port, waits for the debugger to connect """
    s = socket(AF_INET, SOCK_STREAM)
    s.settimeout(None)

    try:
        from socket import SO_REUSEPORT
        s.setsockopt(SOL_SOCKET, SO_REUSEPORT, 1)
    except ImportError:
        s.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)

    s.bind(('', port))
    pydevd_log(1, "Bound to port ", str(port))

    try:
        s.listen(1)
        newSock, _addr = s.accept()
        pydevd_log(1, "Connection accepted")
        # closing server socket is not necessary but we don't need it
        s.shutdown(SHUT_RDWR)
        s.close()
        return newSock

    except:
        sys.stderr.write("Could not bind to port: %s\n" % (port,))
        sys.stderr.flush()
        traceback.print_exc()

#=======================================================================================================================
# start_client
#=======================================================================================================================
def start_client(host, port):
    """ connects to a host/port """
    pydevd_log(1, "Connecting to ", host, ":", str(port))

    s = socket(AF_INET, SOCK_STREAM)

    #  Set TCP keepalive on an open socket.
    #  It activates after 1 second (TCP_KEEPIDLE,) of idleness,
    #  then sends a keepalive ping once every 3 seconds (TCP_KEEPINTVL),
    #  and closes the connection after 5 failed ping (TCP_KEEPCNT), or 15 seconds
    try:
        from socket import IPPROTO_TCP, SO_KEEPALIVE, TCP_KEEPIDLE, TCP_KEEPINTVL, TCP_KEEPCNT
        s.setsockopt(SOL_SOCKET, SO_KEEPALIVE, 1)
        s.setsockopt(IPPROTO_TCP, TCP_KEEPIDLE, 1)
        s.setsockopt(IPPROTO_TCP, TCP_KEEPINTVL, 3)
        s.setsockopt(IPPROTO_TCP, TCP_KEEPCNT, 5)
    except ImportError:
        pass  # May not be available everywhere.

    try:
        s.settimeout(10)  # 10 seconds timeout
        s.connect((host, port))
        s.settimeout(None)  # no timeout after connected
        pydevd_log(1, "Connected.")
        return s
    except:
        sys.stderr.write("Could not connect to %s: %s\n" % (host, port))
        sys.stderr.flush()
        traceback.print_exc()
        raise




#------------------------------------------------------------------------------------ MANY COMMUNICATION STUFF

#=======================================================================================================================
# NetCommand
#=======================================================================================================================
class NetCommand:
    """ Commands received/sent over the network.

    Command can represent command received from the debugger,
    or one to be sent by daemon.
    """
    next_seq = 0 # sequence numbers

    # Protocol where each line is a new message (text is quoted to prevent new lines).
    QUOTED_LINE_PROTOCOL = 'quoted-line'

    # Uses http protocol to provide a new message.
    # i.e.: Content-Length:xxx\r\n\r\npayload
    HTTP_PROTOCOL = 'http'

    protocol = QUOTED_LINE_PROTOCOL

    _showing_debug_info = 0
    _show_debug_info_lock = threading.RLock()

    def __init__(self, cmd_id, seq, text):
        """
        If sequence is 0, new sequence will be generated (otherwise, this was the response
        to a command from the client).
        """
        self.id = cmd_id
        if seq == 0:
            NetCommand.next_seq += 2
            seq = NetCommand.next_seq
        self.seq = seq

        if IS_PY2:
            if isinstance(text, unicode):
                text = text.encode('utf-8')
            else:
                assert isinstance(text, str)
        else:
            assert isinstance(text, str)

        if DebugInfoHolder.DEBUG_TRACE_LEVEL >= 1:
            self._show_debug_info(cmd_id, seq, text)

        if self.protocol == self.HTTP_PROTOCOL:
            msg = '%s\t%s\t%s\n' % (cmd_id, seq, text)
        else:
            encoded = quote(to_string(text), '/<>_=" \t')
            msg = '%s\t%s\t%s\n' % (cmd_id, seq, encoded)


        if IS_PY2:
            assert isinstance(msg, str)  # i.e.: bytes
            as_bytes = msg
        else:
            if isinstance(msg, str):
                msg = msg.encode('utf-8')

            assert isinstance(msg, bytes)
            as_bytes = msg
        self._as_bytes = as_bytes

    def send(self, sock):
        as_bytes = self._as_bytes
        if self.protocol == self.HTTP_PROTOCOL:
            sock.sendall(('Content-Length: %s\r\n\r\n' % len(as_bytes)).encode('ascii'))

        sock.sendall(as_bytes)

    @classmethod
    def _show_debug_info(cls, cmd_id, seq, text):
        with cls._show_debug_info_lock:
            # Only one thread each time (rlock).
            if cls._showing_debug_info:
                # avoid recursing in the same thread (just printing could create
                # a new command when redirecting output).
                return

            cls._showing_debug_info += 1
            try:
                out_message = 'sending cmd --> '
                out_message += "%20s" % ID_TO_MEANING.get(str(cmd_id), 'UNKNOWN')
                out_message += ' '
                out_message += text.replace('\n', ' ')
                try:
                    sys.stderr.write('%s\n' % (out_message,))
                except:
                    pass
            finally:
                cls._showing_debug_info -= 1


#=======================================================================================================================
# NetCommandFactory
#=======================================================================================================================
class NetCommandFactory:

    def _thread_to_xml(self, thread):
        """ thread information as XML """
        name = pydevd_xml.make_valid_xml_value(thread.getName())
        cmdText = '<thread name="%s" id="%s" />' % (quote(name), get_thread_id(thread))
        return cmdText

    def make_error_message(self, seq, text):
        cmd = NetCommand(CMD_ERROR, seq, text)
        if DebugInfoHolder.DEBUG_TRACE_LEVEL > 2:
            sys.stderr.write("Error: %s" % (text,))
        return cmd

    def make_protocol_set_message(self, seq):
        return NetCommand(CMD_SET_PROTOCOL, seq, '')

    def make_thread_created_message(self, thread):
        cmdText = "<xml>" + self._thread_to_xml(thread) + "</xml>"
        return NetCommand(CMD_THREAD_CREATE, 0, cmdText)

    def make_process_created_message(self):
        cmdText = '<process/>'
        return NetCommand(CMD_PROCESS_CREATED, 0, cmdText)

    def make_show_warning_message(self, message_id):
        try:
            cmdText = '<xml><warning id="%s" /></xml>' % message_id
            return NetCommand(CMD_SHOW_CYTHON_WARNING, 0, cmdText)
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_custom_frame_created_message(self, frameId, frameDescription):
        frameDescription = pydevd_xml.make_valid_xml_value(frameDescription)
        cmdText = '<xml><thread name="%s" id="%s"/></xml>' % (frameDescription, frameId)
        return NetCommand(CMD_THREAD_CREATE, 0, cmdText)


    def make_list_threads_message(self, seq):
        """ returns thread listing as XML """
        try:
            threads = get_non_pydevd_threads()
            cmd_text = ["<xml>"]
            append = cmd_text.append
            for thread in threads:
                if is_thread_alive(thread):
                    append(self._thread_to_xml(thread))
            append("</xml>")
            return NetCommand(CMD_RETURN, seq, ''.join(cmd_text))
        except:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_get_thread_stack_message(self, seq, thread_id, topmost_frame, must_be_suspended=False):
        """
        Returns thread stack as XML.

        :param be_suspended: If True and the thread is not suspended, returns None.
        """
        try:
            # If frame is None, the return is an empty frame list.
            cmd_text = ['<xml><thread id="%s">' % (thread_id,)]

            if topmost_frame is not None:
                try:
                    # Note: if we detect that we're already stopped in a given place within
                    # the debugger, use that stack instead of creating a new one with the
                    # current position (this is needed because when an uncaught exception
                    # is reported for a given frame we are actually stopped in a different
                    # place within the debugger).
                    frame = topmost_frame
                    thread_stack_str = ''
                    while frame is not None:
                        if frame.f_code.co_name == 'do_wait_suspend' and frame.f_code.co_filename.endswith('pydevd.py'):
                            thread_stack_str = frame.f_locals.get('thread_stack_str')
                            break
                        frame = frame.f_back
                    else:
                        # Could not find stack of suspended frame...
                        if must_be_suspended:
                            return None
                    cmd_text.append(thread_stack_str or self.make_thread_stack_str(topmost_frame))
                finally:
                    topmost_frame = None
            cmd_text.append('</thread></xml>')
            return NetCommand(CMD_GET_THREAD_STACK, seq, ''.join(cmd_text))
        except:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_variable_changed_message(self, seq, payload):
        # notify debugger that value was changed successfully
        return NetCommand(CMD_RETURN, seq, payload)

    def make_io_message(self, v, ctx):
        '''
        @param v: the message to pass to the debug server
        @param ctx: 1 for stdio 2 for stderr
        '''

        try:
            if len(v) > MAX_IO_MSG_SIZE:
                v = v[0:MAX_IO_MSG_SIZE]
                v += '...'

            v = pydevd_xml.make_valid_xml_value(quote(v, '/>_= '))
            return NetCommand(str(CMD_WRITE_TO_CONSOLE), 0, '<xml><io s="%s" ctx="%s"/></xml>' % (v, ctx))
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_version_message(self, seq):
        try:
            return NetCommand(CMD_VERSION, seq, VERSION_STRING)
        except:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_thread_killed_message(self, id):
        try:
            return NetCommand(CMD_THREAD_KILL, 0, str(id))
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_thread_stack_str(self, frame, frame_to_lineno=None):
        '''
        :param frame_to_lineno:
            If available, the line number for the frame will be gotten from this dict,
            otherwise frame.f_lineno will be used (needed for unhandled exceptions as
            the place where we report may be different from the place where it's raised).
        '''
        if frame_to_lineno is None:
            frame_to_lineno = {}
        make_valid_xml_value = pydevd_xml.make_valid_xml_value
        cmd_text_list = []
        append = cmd_text_list.append

        curr_frame = frame
        frame = None  # Clear frame reference
        try:
            while curr_frame:
                my_id = id(curr_frame)

                if curr_frame.f_code is None:
                    break  # Iron Python sometimes does not have it!

                method_name = curr_frame.f_code.co_name  # method name (if in method) or ? if global
                if method_name is None:
                    break  # Iron Python sometimes does not have it!

                abs_path_real_path_and_base = get_abs_path_real_path_and_base_from_frame(curr_frame)
                if get_file_type(abs_path_real_path_and_base[2]) == PYDEV_FILE:
                    # Syntax errors are a special case in which we don't want to skip the debugger files.
                    # When a syntax error happens, we stop either in the `execfile` or `_exec` function.
                    exception_info, is_syntax_error = curr_frame.f_locals.get('__exception__'), False
                    if exception_info:
                        is_syntax_error = exception_info[0] is SyntaxError
                    if not is_syntax_error:
                        # Skip pydevd files.
                        curr_frame = curr_frame.f_back
                        continue

                my_file = abs_path_real_path_and_base[0]

                if is_real_file(my_file):
                    # if filename is Jupyter cell id
                    my_file = pydevd_file_utils.norm_file_to_client(abs_path_real_path_and_base[0])

                if file_system_encoding.lower() != "utf-8" and hasattr(my_file, "decode"):
                    # my_file is a byte string encoded using the file system encoding
                    # convert it to utf8
                    my_file = my_file.decode(file_system_encoding).encode("utf-8")

                #print "file is ", my_file
                #my_file = inspect.getsourcefile(curr_frame) or inspect.getfile(frame)

                lineno = frame_to_lineno.get(curr_frame, curr_frame.f_lineno)
                # print("line is ", lineno)

                # Note: variables are all gotten 'on-demand'.
                append('<frame id="%s" name="%s" ' % (my_id , make_valid_xml_value(method_name)))
                append('file="%s" line="%s">' % (quote(make_valid_xml_value(my_file), '/>_= \t'), lineno))
                append("</frame>")
                curr_frame = curr_frame.f_back
        except:
            traceback.print_exc()

        curr_frame = None  # Clear frame reference
        return ''.join(cmd_text_list)

    def make_thread_suspend_str(
            self,
            thread_id,
            frame,
            stop_reason=None,
            message=None,
            suspend_type="trace",
            frame_to_lineno=None
    ):
        """
        :return tuple(str,str):
            Returns tuple(thread_suspended_str, thread_stack_str).

            i.e.:
            (
                '''
                    <xml>
                        <thread id="id" stop_reason="reason">
                            <frame id="id" name="functionName " file="file" line="line">
                            </frame>
                        </thread>
                    </xml>
                '''
                ,
                '''
                <frame id="id" name="functionName " file="file" line="line">
                </frame>
                '''
            )
        """
        make_valid_xml_value = pydevd_xml.make_valid_xml_value
        cmd_text_list = []
        append = cmd_text_list.append

        cmd_text_list.append('<xml>')
        if message:
            message = make_valid_xml_value(message)

        append('<thread id="%s"' % (thread_id,))
        if stop_reason is not None:
            append(' stop_reason="%s"' % (stop_reason,))
        if message is not None:
            append(' message="%s"' % (message,))
        if suspend_type is not None:
            append(' suspend_type="%s"' % (suspend_type,))
        append('>')
        thread_stack_str = self.make_thread_stack_str(frame, frame_to_lineno)
        append(thread_stack_str)
        append("</thread></xml>")

        return ''.join(cmd_text_list), thread_stack_str

    def make_thread_suspend_message(self, thread_id, frame, stop_reason, message, suspend_type, frame_to_lineno=None):
        try:
            thread_suspend_str, thread_stack_str = self.make_thread_suspend_str(
                thread_id, frame, stop_reason, message, suspend_type, frame_to_lineno=frame_to_lineno)
            cmd = NetCommand(CMD_THREAD_SUSPEND, 0, thread_suspend_str)
            cmd.thread_stack_str = thread_stack_str
            cmd.thread_suspend_str = thread_suspend_str
            return cmd
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_thread_suspend_single_notification(self, thread_id, stop_reason):
        try:
            return NetCommand(CMD_THREAD_SUSPEND_SINGLE_NOTIFICATION, 0, json.dumps(
                {'thread_id': thread_id, 'stop_reason': stop_reason}))
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_thread_resume_single_notification(self, thread_id):
        try:
            return NetCommand(CMD_THREAD_RESUME_SINGLE_NOTIFICATION, 0, json.dumps(
                {'thread_id': thread_id}))
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_thread_run_message(self, thread_id, reason):
        try:
            return NetCommand(CMD_THREAD_RUN, 0, "%s\t%s" % (thread_id, reason))
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_get_variable_message(self, seq, payload):
        try:
            return NetCommand(CMD_GET_VARIABLE, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())


    def make_get_array_message(self, seq, payload):
        try:
            return NetCommand(CMD_GET_ARRAY, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_get_description_message(self, seq, payload):
        try:
            return NetCommand(CMD_GET_DESCRIPTION, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_get_frame_message(self, seq, payload):
        try:
            return NetCommand(CMD_GET_FRAME, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())


    def make_evaluate_expression_message(self, seq, payload):
        try:
            return NetCommand(CMD_EVALUATE_EXPRESSION, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_get_completions_message(self, seq, payload):
        try:
            return NetCommand(CMD_GET_COMPLETIONS, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_get_file_contents(self, seq, payload):
        try:
            return NetCommand(CMD_GET_FILE_CONTENTS, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_send_breakpoint_exception_message(self, seq, payload):
        try:
            return NetCommand(CMD_GET_BREAKPOINT_EXCEPTION, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def _make_send_curr_exception_trace_str(self, thread_id, exc_type, exc_desc, trace_obj):
        while trace_obj.tb_next is not None:
            trace_obj = trace_obj.tb_next

        exc_type = pydevd_xml.make_valid_xml_value(str(exc_type)).replace('\t', '  ') or 'exception: type unknown'
        exc_desc = pydevd_xml.make_valid_xml_value(str(exc_desc)).replace('\t', '  ') or 'exception: no description'

        thread_suspend_str, thread_stack_str = self.make_thread_suspend_str(
            thread_id, trace_obj.tb_frame, CMD_SEND_CURR_EXCEPTION_TRACE, '')
        return exc_type, exc_desc, thread_suspend_str, thread_stack_str

    def make_send_curr_exception_trace_message(self, seq, thread_id, curr_frame_id, exc_type, exc_desc, trace_obj):
        try:
            exc_type, exc_desc, thread_suspend_str, _thread_stack_str = self._make_send_curr_exception_trace_str(
                thread_id, exc_type, exc_desc, trace_obj)
            payload = str(curr_frame_id) + '\t' + exc_type + "\t" + exc_desc + "\t" + thread_suspend_str
            return NetCommand(CMD_SEND_CURR_EXCEPTION_TRACE, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_get_exception_details_message(self, seq, thread_id, topmost_frame):
        """Returns exception details as XML """
        try:
            # If the debugger is not suspended, just return the thread and its id.
            cmd_text = ['<xml><thread id="%s" ' % (thread_id,)]

            if topmost_frame is not None:
                try:
                    frame = topmost_frame
                    topmost_frame = None
                    while frame is not None:
                        if frame.f_code.co_name == 'do_wait_suspend' and frame.f_code.co_filename.endswith('pydevd.py'):
                            arg = frame.f_locals.get('arg', None)
                            if arg is not None:
                                exc_type, exc_desc, _thread_suspend_str, thread_stack_str = self._make_send_curr_exception_trace_str(
                                    thread_id, *arg)
                                cmd_text.append('exc_type="%s" ' % (exc_type,))
                                cmd_text.append('exc_desc="%s" ' % (exc_desc,))
                                cmd_text.append('>')
                                cmd_text.append(thread_stack_str)
                                break
                        frame = frame.f_back
                    else:
                        cmd_text.append('>')
                finally:
                    frame = None
            cmd_text.append('</thread></xml>')
            return NetCommand(CMD_GET_EXCEPTION_DETAILS, seq, ''.join(cmd_text))
        except:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_send_curr_exception_trace_proceeded_message(self, seq, thread_id):
        try:
            return NetCommand(CMD_SEND_CURR_EXCEPTION_TRACE_PROCEEDED, 0, str(thread_id))
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_send_console_message(self, seq, payload):
        try:
            return NetCommand(CMD_EVALUATE_CONSOLE_EXPRESSION, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_custom_operation_message(self, seq, payload):
        try:
            return NetCommand(CMD_RUN_CUSTOM_OPERATION, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_load_source_message(self, seq, source, dbg=None):
        try:
            net = NetCommand(CMD_LOAD_SOURCE, seq, '%s' % source)

        except:
            net = self.make_error_message(0, get_exception_traceback_str())

        if dbg:
            dbg.writer.add_command(net)
        return net

    def make_show_console_message(self, thread_id, frame):
        try:
            thread_suspended_str, _thread_stack_str = self.make_thread_suspend_str(thread_id, frame, CMD_SHOW_CONSOLE, '')
            return NetCommand(CMD_SHOW_CONSOLE, 0, thread_suspended_str)
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_input_requested_message(self, started):
        try:
            return NetCommand(CMD_INPUT_REQUESTED, 0, str(started))
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_set_next_stmnt_status_message(self, seq, is_success, exception_msg):
        try:
            message = str(is_success) + '\t' + exception_msg
            return NetCommand(CMD_SET_NEXT_STATEMENT, int(seq), message)
        except:
            return self.make_error_message(0, get_exception_traceback_str())

    def make_load_full_value_message(self, seq, payload):
        try:
            return NetCommand(CMD_LOAD_FULL_VALUE, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())

    def make_exit_message(self):
        try:
            net = NetCommand(CMD_EXIT, 0, '')

        except:
            net = self.make_error_message(0, get_exception_traceback_str())

        return net

    def make_get_next_statement_targets_message(self, seq, payload):
        try:
            return NetCommand(CMD_GET_NEXT_STATEMENT_TARGETS, seq, payload)
        except Exception:
            return self.make_error_message(seq, get_exception_traceback_str())


INTERNAL_TERMINATE_THREAD = 1
INTERNAL_SUSPEND_THREAD = 2


#=======================================================================================================================
# InternalThreadCommand
#=======================================================================================================================
class InternalThreadCommand:
    """ internal commands are generated/executed by the debugger.

    The reason for their existence is that some commands have to be executed
    on specific threads. These are the InternalThreadCommands that get
    get posted to PyDB.cmdQueue.
    """

    def __init__(self, thread_id):
        self.thread_id = thread_id

    def can_be_executed_by(self, thread_id):
        '''By default, it must be in the same thread to be executed
        '''
        return self.thread_id == thread_id or self.thread_id.endswith('|' + thread_id)

    def do_it(self, dbg):
        raise NotImplementedError("you have to override do_it")


class ReloadCodeCommand(InternalThreadCommand):


    def __init__(self, module_name, thread_id):
        self.thread_id = thread_id
        self.module_name = module_name
        self.executed = False
        self.lock = thread.allocate_lock()


    def can_be_executed_by(self, thread_id):
        if self.thread_id == '*':
            return True  #Any thread can execute it!

        return InternalThreadCommand.can_be_executed_by(self, thread_id)


    def do_it(self, dbg):
        self.lock.acquire()
        try:
            if self.executed:
                return
            self.executed = True
        finally:
            self.lock.release()

        module_name = self.module_name
        if module_name not in sys.modules:
            if '.' in module_name:
                new_module_name = module_name.split('.')[-1]
                if new_module_name in sys.modules:
                    module_name = new_module_name

        if module_name not in sys.modules:
            sys.stderr.write('pydev debugger: Unable to find module to reload: "' + module_name + '".\n')
            # Too much info...
            # sys.stderr.write('pydev debugger: This usually means you are trying to reload the __main__ module (which cannot be reloaded).\n')

        else:
            sys.stderr.write('pydev debugger: Start reloading module: "' + module_name + '" ... \n')
            from _pydevd_bundle import pydevd_reload
            if pydevd_reload.xreload(sys.modules[module_name]):
                sys.stderr.write('pydev debugger: reload finished\n')
            else:
                sys.stderr.write('pydev debugger: reload finished without applying any change\n')


#=======================================================================================================================
# InternalGetThreadStack
#=======================================================================================================================
class InternalGetThreadStack(InternalThreadCommand):
    '''
    This command will either wait for a given thread to be paused to get its stack or will provide
    it anyways after a timeout (in which case the stack will be gotten but local variables won't
    be available and it'll not be possible to interact with the frame as it's not actually
    stopped in a breakpoint).
    '''

    def __init__(self, seq, thread_id, py_db, set_additional_thread_info, timeout=.5):
        InternalThreadCommand.__init__(self, thread_id)
        self._py_db = weakref.ref(py_db)
        self._timeout = time.time() + timeout
        self.seq = seq
        self._cmd = None

        # Note: receives set_additional_thread_info to avoid a circular import
        # in this module.
        self._set_additional_thread_info = set_additional_thread_info

    @overrides(InternalThreadCommand.can_be_executed_by)
    def can_be_executed_by(self, _thread_id):
        timed_out = time.time() >= self._timeout

        py_db = self._py_db()
        t = pydevd_find_thread_by_id(self.thread_id)
        frame = None
        if t and not getattr(t, 'pydev_do_not_trace', None):
            additional_info = self._set_additional_thread_info(t)
            frame = additional_info.get_topmost_frame(t)
        try:
            self._cmd = py_db.cmd_factory.make_get_thread_stack_message(
                self.seq, self.thread_id, frame, must_be_suspended=not timed_out)
        finally:
            frame = None
            t = None

        return self._cmd is not None or timed_out

    @overrides(InternalThreadCommand.do_it)
    def do_it(self, dbg):
        if self._cmd is not None:
            dbg.writer.add_command(self._cmd)
            self._cmd = None


#=======================================================================================================================
# InternalRunThread
#=======================================================================================================================
class InternalRunThread(InternalThreadCommand):

    def do_it(self, dbg):
        t = pydevd_find_thread_by_id(self.thread_id)
        if t:
            t.additional_info.pydev_step_cmd = -1
            t.additional_info.pydev_step_stop = None
            t.additional_info.pydev_state = STATE_RUN


#=======================================================================================================================
# InternalStepThread
#=======================================================================================================================
class InternalStepThread(InternalThreadCommand):
    def __init__(self, thread_id, cmd_id):
        self.thread_id = thread_id
        self.cmd_id = cmd_id

    def do_it(self, dbg):
        t = pydevd_find_thread_by_id(self.thread_id)
        if t:
            t.additional_info.pydev_step_cmd = self.cmd_id
            t.additional_info.pydev_state = STATE_RUN


#=======================================================================================================================
# InternalSetNextStatementThread
#=======================================================================================================================
class InternalSetNextStatementThread(InternalThreadCommand):
    def __init__(self, thread_id, cmd_id, line, func_name, seq=0):
        self.thread_id = thread_id
        self.cmd_id = cmd_id
        self.line = line
        self.seq = seq

        if IS_PY2:
            if isinstance(func_name, unicode):
                # On cython with python 2.X it requires an str, not unicode (but on python 3.3 it should be a str, not bytes).
                func_name = func_name.encode('utf-8')

        self.func_name = func_name

    def do_it(self, dbg):
        t = pydevd_find_thread_by_id(self.thread_id)
        if t:
            t.additional_info.pydev_step_cmd = self.cmd_id
            t.additional_info.pydev_next_line = int(self.line)
            t.additional_info.pydev_func_name = self.func_name
            t.additional_info.pydev_state = STATE_RUN
            t.additional_info.pydev_message = str(self.seq)


#=======================================================================================================================
# InternalGetVariable
#=======================================================================================================================
class InternalGetVariable(InternalThreadCommand):
    """ gets the value of a variable """
    def __init__(self, seq, thread_id, frame_id, scope, attrs):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.scope = scope
        self.attributes = attrs

    def do_it(self, dbg):
        """ Converts request into python variable """
        try:
            xml = StringIO.StringIO()
            xml.write("<xml>")
            _typeName, val_dict = pydevd_vars.resolve_compound_variable_fields(self.thread_id, self.frame_id, self.scope, self.attributes)
            if val_dict is None:
                val_dict = {}

            # assume properly ordered if resolver returns 'OrderedDict'
            # check type as string to support OrderedDict backport for older Python
            keys = dict_keys(val_dict)
            if not (_typeName == "OrderedDict" or val_dict.__class__.__name__ == "OrderedDict" or IS_PY36_OR_GREATER):
                keys.sort(key=compare_object_attrs_key)

            for k in keys:
                val = val_dict[k]
                evaluate_full_value = pydevd_xml.should_evaluate_full_value(val)
                xml.write(pydevd_xml.var_to_xml(val, k, evaluate_full_value=evaluate_full_value))

            xml.write("</xml>")
            cmd = dbg.cmd_factory.make_get_variable_message(self.sequence, xml.getvalue())
            xml.close()
            dbg.writer.add_command(cmd)
        except Exception:
            cmd = dbg.cmd_factory.make_error_message(
                self.sequence, "Error resolving variables %s" % (get_exception_traceback_str(),))
            dbg.writer.add_command(cmd)


#=======================================================================================================================
# InternalGetArray
#=======================================================================================================================
class InternalGetArray(InternalThreadCommand):
    def __init__(self, seq, roffset, coffset, rows, cols, format, thread_id, frame_id, scope, attrs):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.scope = scope
        self.name = attrs.split("\t")[-1]
        self.attrs = attrs
        self.roffset = int(roffset)
        self.coffset = int(coffset)
        self.rows = int(rows)
        self.cols = int(cols)
        self.format = format

    def do_it(self, dbg):
        try:
            frame = pydevd_vars.find_frame(self.thread_id, self.frame_id)
            var = pydevd_vars.eval_in_context(self.name, frame.f_globals, frame.f_locals)
            xml = pydevd_vars.table_like_struct_to_xml(var, self.name, self.roffset, self.coffset, self.rows, self.cols, self.format )
            cmd = dbg.cmd_factory.make_get_array_message(self.sequence, xml)
            dbg.writer.add_command(cmd)
        except:
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error resolving array: " + get_exception_traceback_str())
            dbg.writer.add_command(cmd)

#=======================================================================================================================
# InternalChangeVariable
#=======================================================================================================================
class InternalChangeVariable(InternalThreadCommand):
    """ changes the value of a variable """
    def __init__(self, seq, thread_id, frame_id, scope, attr, expression):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.scope = scope
        self.attr = attr
        self.expression = expression

    def do_it(self, dbg):
        """ Converts request into python variable """
        try:
            result = pydevd_vars.change_attr_expression(self.thread_id, self.frame_id, self.attr, self.expression, dbg)
            xml = "<xml>"
            xml += pydevd_xml.var_to_xml(result, "")
            xml += "</xml>"
            cmd = dbg.cmd_factory.make_variable_changed_message(self.sequence, xml)
            dbg.writer.add_command(cmd)
        except Exception:
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error changing variable attr:%s expression:%s traceback:%s" % (self.attr, self.expression, get_exception_traceback_str()))
            dbg.writer.add_command(cmd)


#=======================================================================================================================
# InternalGetFrame
#=======================================================================================================================
class InternalGetFrame(InternalThreadCommand):
    """ gets the value of a variable """
    def __init__(self, seq, thread_id, frame_id):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id

    def do_it(self, dbg):
        """ Converts request into python variable """
        try:
            frame = pydevd_vars.find_frame(self.thread_id, self.frame_id)
            if frame is not None:
                hidden_ns = pydevd_console_integration.get_ipython_hidden_vars()
                xml = "<xml>"
                xml += pydevd_xml.frame_vars_to_xml(frame.f_locals, hidden_ns)
                del frame
                xml += "</xml>"
                cmd = dbg.cmd_factory.make_get_frame_message(self.sequence, xml)
                dbg.writer.add_command(cmd)
            else:
                #pydevd_vars.dump_frames(self.thread_id)
                #don't print this error: frame not found: means that the client is not synchronized (but that's ok)
                cmd = dbg.cmd_factory.make_error_message(self.sequence, "Frame not found: %s from thread: %s" % (self.frame_id, self.thread_id))
                dbg.writer.add_command(cmd)
        except:
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error resolving frame: %s from thread: %s" % (self.frame_id, self.thread_id))
            dbg.writer.add_command(cmd)

#=======================================================================================================================
# InternalGetNextStatementTargets
#=======================================================================================================================
class InternalGetNextStatementTargets(InternalThreadCommand):
    """ gets the valid line numbers for use with set next statement """
    def __init__(self, seq, thread_id, frame_id):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id

    def do_it(self, dbg):
        """ Converts request into set of line numbers """
        try:
            frame = pydevd_vars.find_frame(self.thread_id, self.frame_id)
            if frame is not None:
                code = frame.f_code
                xml = "<xml>"
                if hasattr(code, 'co_lnotab'):
                    lineno = code.co_firstlineno
                    lnotab = code.co_lnotab
                    for i in itertools.islice(lnotab, 1, len(lnotab), 2):
                        if isinstance(i, int):
                            lineno = lineno + i
                        else:
                            # in python 2 elements in co_lnotab are of type str
                            lineno = lineno + ord(i)
                        xml += "<line>%d</line>" % (lineno,)
                else:
                    xml += "<line>%d</line>" % (frame.f_lineno,)
                del frame
                xml += "</xml>"
                cmd = dbg.cmd_factory.make_get_next_statement_targets_message(self.sequence, xml)
                dbg.writer.add_command(cmd)
            else:
                cmd = dbg.cmd_factory.make_error_message(self.sequence, "Frame not found: %s from thread: %s" % (self.frame_id, self.thread_id))
                dbg.writer.add_command(cmd)
        except:
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error resolving frame: %s from thread: %s" % (self.frame_id, self.thread_id))
            dbg.writer.add_command(cmd)

#=======================================================================================================================
# InternalEvaluateExpression
#=======================================================================================================================
class InternalEvaluateExpression(InternalThreadCommand):
    """ gets the value of a variable """

    def __init__(self, seq, thread_id, frame_id, expression, doExec, doTrim, temp_name):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.expression = expression
        self.doExec = doExec
        self.doTrim = doTrim
        self.temp_name = temp_name

    def do_it(self, dbg):
        """ Converts request into python variable """
        try:
            result = pydevd_vars.evaluate_expression(self.thread_id, self.frame_id, self.expression, self.doExec)
            if self.temp_name != "":
                pydevd_vars.change_attr_expression(self.thread_id, self.frame_id, self.temp_name, self.expression, dbg, result)
            xml = "<xml>"
            xml += pydevd_xml.var_to_xml(result, self.expression, self.doTrim)
            xml += "</xml>"
            cmd = dbg.cmd_factory.make_evaluate_expression_message(self.sequence, xml)
            dbg.writer.add_command(cmd)
        except:
            exc = get_exception_traceback_str()
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error evaluating expression " + exc)
            dbg.writer.add_command(cmd)

#=======================================================================================================================
# InternalGetCompletions
#=======================================================================================================================
class InternalGetCompletions(InternalThreadCommand):
    """ Gets the completions in a given scope """

    def __init__(self, seq, thread_id, frame_id, act_tok):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.act_tok = act_tok


    def do_it(self, dbg):
        """ Converts request into completions """
        try:
            remove_path = None
            try:

                frame = pydevd_vars.find_frame(self.thread_id, self.frame_id)
                if frame is not None:

                    msg = _pydev_completer.generate_completions_as_xml(frame, self.act_tok)

                    cmd = dbg.cmd_factory.make_get_completions_message(self.sequence, msg)
                    dbg.writer.add_command(cmd)
                else:
                    cmd = dbg.cmd_factory.make_error_message(self.sequence, "InternalGetCompletions: Frame not found: %s from thread: %s" % (self.frame_id, self.thread_id))
                    dbg.writer.add_command(cmd)


            finally:
                if remove_path is not None:
                    sys.path.remove(remove_path)

        except:
            exc = get_exception_traceback_str()
            sys.stderr.write('%s\n' % (exc,))
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error evaluating expression " + exc)
            dbg.writer.add_command(cmd)


# =======================================================================================================================
# InternalGetDescription
# =======================================================================================================================
class InternalGetDescription(InternalThreadCommand):
    """ Fetch the variable description stub from the debug console
    """

    def __init__(self, seq, thread_id, frame_id, expression):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.expression = expression

    def do_it(self, dbg):
        """ Get completions and write back to the client
        """
        try:
            frame = pydevd_vars.find_frame(self.thread_id, self.frame_id)
            description = pydevd_console.get_description(frame, self.thread_id, self.frame_id, self.expression)
            description = pydevd_xml.make_valid_xml_value(quote(description, '/>_= \t'))
            description_xml = '<xml><var name="" type="" value="%s"/></xml>' % description
            cmd = dbg.cmd_factory.make_get_description_message(self.sequence, description_xml)
            dbg.writer.add_command(cmd)
        except:
            exc = get_exception_traceback_str()
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error in fetching description" + exc)
            dbg.writer.add_command(cmd)


#=======================================================================================================================
# InternalGetBreakpointException
#=======================================================================================================================
class InternalGetBreakpointException(InternalThreadCommand):
    """ Send details of exception raised while evaluating conditional breakpoint """
    def __init__(self, thread_id, exc_type, stacktrace):
        self.sequence = 0
        self.thread_id = thread_id
        self.stacktrace = stacktrace
        self.exc_type = exc_type

    def do_it(self, dbg):
        try:
            callstack = "<xml>"

            makeValid = pydevd_xml.make_valid_xml_value

            for filename, line, methodname, methodobj in self.stacktrace:
                if file_system_encoding.lower() != "utf-8" and hasattr(filename, "decode"):
                    # filename is a byte string encoded using the file system encoding
                    # convert it to utf8
                    filename = filename.decode(file_system_encoding).encode("utf-8")

                callstack += '<frame thread_id = "%s" file="%s" line="%s" name="%s" obj="%s" />' \
                             % (self.thread_id, makeValid(filename), line, makeValid(methodname), makeValid(methodobj))
            callstack += "</xml>"

            cmd = dbg.cmd_factory.make_send_breakpoint_exception_message(self.sequence, self.exc_type + "\t" + callstack)
            dbg.writer.add_command(cmd)
        except:
            exc = get_exception_traceback_str()
            sys.stderr.write('%s\n' % (exc,))
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error Sending Exception: " + exc)
            dbg.writer.add_command(cmd)


#=======================================================================================================================
# InternalSendCurrExceptionTrace
#=======================================================================================================================
class InternalSendCurrExceptionTrace(InternalThreadCommand):
    """ Send details of the exception that was caught and where we've broken in.
    """
    def __init__(self, thread_id, arg, curr_frame_id):
        '''
        :param arg: exception type, description, traceback object
        '''
        self.sequence = 0
        self.thread_id = thread_id
        self.curr_frame_id = curr_frame_id
        self.arg = arg

    def do_it(self, dbg):
        try:
            cmd = dbg.cmd_factory.make_send_curr_exception_trace_message(self.sequence, self.thread_id, self.curr_frame_id, *self.arg)
            del self.arg
            dbg.writer.add_command(cmd)
        except:
            exc = get_exception_traceback_str()
            sys.stderr.write('%s\n' % (exc,))
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error Sending Current Exception Trace: " + exc)
            dbg.writer.add_command(cmd)

#=======================================================================================================================
# InternalSendCurrExceptionTraceProceeded
#=======================================================================================================================
class InternalSendCurrExceptionTraceProceeded(InternalThreadCommand):
    """ Send details of the exception that was caught and where we've broken in.
    """
    def __init__(self, thread_id):
        self.sequence = 0
        self.thread_id = thread_id

    def do_it(self, dbg):
        try:
            cmd = dbg.cmd_factory.make_send_curr_exception_trace_proceeded_message(self.sequence, self.thread_id)
            dbg.writer.add_command(cmd)
        except:
            exc = get_exception_traceback_str()
            sys.stderr.write('%s\n' % (exc,))
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error Sending Current Exception Trace Proceeded: " + exc)
            dbg.writer.add_command(cmd)


#=======================================================================================================================
# InternalEvaluateConsoleExpression
#=======================================================================================================================
class InternalEvaluateConsoleExpression(InternalThreadCommand):
    """ Execute the given command in the debug console """

    def __init__(self, seq, thread_id, frame_id, line, buffer_output=True):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.line = line
        self.buffer_output = buffer_output

    def do_it(self, dbg):
        """ Create an XML for console output, error and more (true/false)
        <xml>
            <output message=output_message></output>
            <error message=error_message></error>
            <more>true/false</more>
        </xml>
        """
        try:
            frame = pydevd_vars.find_frame(self.thread_id, self.frame_id)
            if frame is not None:
                console_message = pydevd_console.execute_console_command(
                    frame, self.thread_id, self.frame_id, self.line, self.buffer_output)

                cmd = dbg.cmd_factory.make_send_console_message(self.sequence, console_message.to_xml())
            else:
                from _pydevd_bundle.pydevd_console import ConsoleMessage
                console_message = ConsoleMessage()
                console_message.add_console_message(
                    pydevd_console.CONSOLE_ERROR,
                    "Select the valid frame in the debug view (thread: %s, frame: %s invalid)" % (self.thread_id, self.frame_id),
                    )
                cmd = dbg.cmd_factory.make_error_message(self.sequence, console_message.to_xml())
        except:
            exc = get_exception_traceback_str()
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error evaluating expression " + exc)
        dbg.writer.add_command(cmd)


#=======================================================================================================================
# InternalRunCustomOperation
#=======================================================================================================================
class InternalRunCustomOperation(InternalThreadCommand):
    """ Run a custom command on an expression
    """
    def __init__(self, seq, thread_id, frame_id, scope, attrs, style, encoded_code_or_file, fnname):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.scope = scope
        self.attrs = attrs
        self.style = style
        self.code_or_file = unquote_plus(encoded_code_or_file)
        self.fnname = fnname

    def do_it(self, dbg):
        try:
            res = pydevd_vars.custom_operation(self.thread_id, self.frame_id, self.scope, self.attrs,
                                               self.style, self.code_or_file, self.fnname)
            resEncoded = quote_plus(res)
            cmd = dbg.cmd_factory.make_custom_operation_message(self.sequence, resEncoded)
            dbg.writer.add_command(cmd)
        except:
            exc = get_exception_traceback_str()
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error in running custom operation" + exc)
            dbg.writer.add_command(cmd)


#=======================================================================================================================
# InternalConsoleGetCompletions
#=======================================================================================================================
class InternalConsoleGetCompletions(InternalThreadCommand):
    """ Fetch the completions in the debug console
    """
    def __init__(self, seq, thread_id, frame_id, act_tok):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.act_tok = act_tok

    def do_it(self, dbg):
        """ Get completions and write back to the client
        """
        try:
            frame = pydevd_vars.find_frame(self.thread_id, self.frame_id)
            completions_xml = pydevd_console.get_completions(frame, self.act_tok)
            cmd = dbg.cmd_factory.make_send_console_message(self.sequence, completions_xml)
            dbg.writer.add_command(cmd)
        except:
            exc = get_exception_traceback_str()
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error in fetching completions" + exc)
            dbg.writer.add_command(cmd)


#=======================================================================================================================
# InternalConsoleExec
#=======================================================================================================================
class InternalConsoleExec(InternalThreadCommand):
    """ gets the value of a variable """

    def __init__(self, seq, thread_id, frame_id, expression):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.expression = expression

    def do_it(self, dbg):
        """ Converts request into python variable """
        try:
            try:
                #don't trace new threads created by console command
                disable_trace_thread_modules()

                result = pydevd_console_integration.console_exec(self.thread_id, self.frame_id, self.expression, dbg)
                xml = "<xml>"
                xml += pydevd_xml.var_to_xml(result, "")
                xml += "</xml>"
                cmd = dbg.cmd_factory.make_evaluate_expression_message(self.sequence, xml)
                dbg.writer.add_command(cmd)
            except:
                exc = get_exception_traceback_str()
                sys.stderr.write('%s\n' % (exc,))
                cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error evaluating console expression " + exc)
                dbg.writer.add_command(cmd)
        finally:
            enable_trace_thread_modules()

            sys.stderr.flush()
            sys.stdout.flush()


#=======================================================================================================================
# InternalLoadFullValue
#=======================================================================================================================
class InternalLoadFullValue(InternalThreadCommand):
    """
    Loads values asynchronously
    """
    def __init__(self, seq, thread_id, frame_id, vars):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.vars = vars

    def do_it(self, dbg):
        """Starts a thread that will load values asynchronously"""
        try:
            var_objects = []
            for variable in self.vars:
                variable = variable.strip()
                if len(variable) > 0:
                    if '\t' in variable:  # there are attributes beyond scope
                        scope, attrs = variable.split('\t', 1)
                        name = attrs[0]
                    else:
                        scope, attrs = (variable, None)
                        name = scope
                    var_obj = pydevd_vars.getVariable(self.thread_id, self.frame_id, scope, attrs)
                    var_objects.append((var_obj, name))

            t = GetValueAsyncThreadDebug(dbg, self.sequence, var_objects)
            t.start()
        except:
            exc = get_exception_traceback_str()
            sys.stderr.write('%s\n' % (exc,))
            cmd = dbg.cmd_factory.make_error_message(self.sequence, "Error evaluating variable %s " % exc)
            dbg.writer.add_command(cmd)


class AbstractGetValueAsyncThread(PyDBDaemonThread):
    """
    Abstract class for a thread, which evaluates values for async variables
    """
    def __init__(self, frame_accessor, seq, var_objects):
        PyDBDaemonThread.__init__(self)
        self.frame_accessor = frame_accessor
        self.seq = seq
        self.var_objs = var_objects
        self.cancel_event = threading.Event()

    def send_result(self, xml):
        raise NotImplementedError()

    @overrides(PyDBDaemonThread._on_run)
    def _on_run(self):
        start = time.time()
        xml = StringIO.StringIO()
        xml.write("<xml>")
        for (var_obj, name) in self.var_objs:
            current_time = time.time()
            if current_time - start > ASYNC_EVAL_TIMEOUT_SEC or self.cancel_event.is_set():
                break
            xml.write(pydevd_xml.var_to_xml(var_obj, name, evaluate_full_value=True))
        xml.write("</xml>")
        self.send_result(xml)
        xml.close()


class GetValueAsyncThreadDebug(AbstractGetValueAsyncThread):
    """
    A thread for evaluation async values, which returns result for debugger
    Create message and send it via writer thread
    """
    def send_result(self, xml):
        if self.frame_accessor is not None:
            cmd = self.frame_accessor.cmd_factory.make_load_full_value_message(self.seq, xml.getvalue())
            self.frame_accessor.writer.add_command(cmd)


class GetValueAsyncThreadConsole(AbstractGetValueAsyncThread):
    """
    A thread for evaluation async values, which returns result for Console
    Send result directly to Console's server
    """
    def send_result(self, xml):
        if self.frame_accessor is not None:
            self.frame_accessor.ReturnFullValue(self.seq, xml.getvalue())


#=======================================================================================================================
# pydevd_find_thread_by_id
#=======================================================================================================================
def pydevd_find_thread_by_id(thread_id):
    try:
        # there was a deadlock here when I did not remove the tracing function when thread was dead
        threads = threading.enumerate()
        for i in threads:
            tid = get_thread_id(i)
            if thread_id == tid or thread_id.endswith('|' + tid):
                return i

        # This can happen when a request comes for a thread which was previously removed.
        pydevd_log(1, "Could not find thread %s\n" % thread_id)
        pydevd_log(1, "Available: %s\n" % [get_thread_id(t) for t in threads] % thread_id)
    except:
        traceback.print_exc()

    return None
