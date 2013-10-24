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

500 series diagnostics/ok
    501      VERSION                  either      Version string (1.0)        Currently just used at startup
    502      RETURN                   either      Depends on caller    -

900 series: errors
    901      ERROR                    either      -                           This is reserved for unexpected errors.

    * JAVA - remote debugger, the java end
    * PYDB - pydevd, the python end
'''
from pydevd_constants import * #@UnusedWildImport

import sys

if USE_LIB_COPY:
    import _pydev_time as time
    import _pydev_threading as threading
    try:
        import _pydev_thread as thread
    except ImportError:
        import _thread as thread #Py3K changed it.
    import _pydev_Queue as _queue
    from _pydev_socket import socket
    from _pydev_socket import AF_INET, SOCK_STREAM
    from _pydev_socket import SHUT_RD, SHUT_WR
else:
    import time
    import threading
    try:
        import thread
    except ImportError:
        import _thread as thread #Py3K changed it.

    try:
        import Queue as _queue
    except ImportError:
        import queue as _queue
    from socket import socket
    from socket import AF_INET, SOCK_STREAM
    from socket import SHUT_RD, SHUT_WR

try:
    from urllib import quote
except:
    from urllib.parse import quote #@Reimport @UnresolvedImport

import pydevd_vars
import pydev_log
import pydevd_tracing
import pydevd_vm_type
import pydevd_file_utils
import traceback
from pydevd_utils import *
from pydevd_utils import quote_smart as quote


from pydevd_tracing import GetExceptionTracebackStr
import pydevconsole

try:
    _Thread_stop = threading.Thread._Thread__stop
except AttributeError:
    _Thread_stop = threading.Thread._stop  # _stop in Python 3



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
CMD_VERSION = 501
CMD_RETURN = 502
CMD_ERROR = 901

ID_TO_MEANING = {
    '101':'CMD_RUN',
    '102':'CMD_LIST_THREADS',
    '103':'CMD_THREAD_CREATE',
    '104':'CMD_THREAD_KILL',
    '105':'CMD_THREAD_SUSPEND',
    '106':'CMD_THREAD_RUN',
    '107':'CMD_STEP_INTO',
    '108':'CMD_STEP_OVER',
    '109':'CMD_STEP_RETURN',
    '110':'CMD_GET_VARIABLE',
    '111':'CMD_SET_BREAK',
    '112':'CMD_REMOVE_BREAK',
    '113':'CMD_EVALUATE_EXPRESSION',
    '114':'CMD_GET_FRAME',
    '115':'CMD_EXEC_EXPRESSION',
    '116':'CMD_WRITE_TO_CONSOLE',
    '117':'CMD_CHANGE_VARIABLE',
    '118':'CMD_RUN_TO_LINE',
    '119':'CMD_RELOAD_CODE',
    '120':'CMD_GET_COMPLETIONS',
    '121':'CMD_CONSOLE_EXEC',
    '122':'CMD_ADD_EXCEPTION_BREAK',
    '123':'CMD_REMOVE_EXCEPTION_BREAK',
    '124':'CMD_LOAD_SOURCE',
    '125':'CMD_ADD_DJANGO_EXCEPTION_BREAK',
    '126':'CMD_REMOVE_DJANGO_EXCEPTION_BREAK',
    '127':'CMD_SET_NEXT_STATEMENT',
    '128':'CMD_SMART_STEP_INTO',
    '129': 'CMD_EXIT',
    '130': 'CMD_SIGNATURE_CALL_TRACE',
    '501':'CMD_VERSION',
    '502':'CMD_RETURN',
    '901':'CMD_ERROR',
    }

MAX_IO_MSG_SIZE = 1000  #if the io is too big, we'll not send all (could make the debugger too non-responsive)
#this number can be changed if there's need to do so

VERSION_STRING = "@@BUILD_NUMBER@@"


#--------------------------------------------------------------------------------------------------- UTILITIES

#=======================================================================================================================
# PydevdLog
#=======================================================================================================================
def PydevdLog(level, *args):
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

#=======================================================================================================================
# GlobalDebuggerHolder
#=======================================================================================================================
class GlobalDebuggerHolder:
    '''
        Holder for the global debugger.
    '''
    globalDbg = None

#=======================================================================================================================
# GetGlobalDebugger
#=======================================================================================================================
def GetGlobalDebugger():
    return GlobalDebuggerHolder.globalDbg

#=======================================================================================================================
# SetGlobalDebugger
#=======================================================================================================================
def SetGlobalDebugger(dbg):
    GlobalDebuggerHolder.globalDbg = dbg


#------------------------------------------------------------------- ACTUAL COMM

#=======================================================================================================================
# PyDBDaemonThread
#=======================================================================================================================
class PyDBDaemonThread(threading.Thread):

    def __init__(self):
        threading.Thread.__init__(self)
        self.setDaemon(True)
        self.killReceived = False
        self.dontTraceMe = True

    def run(self):
        if sys.platform.startswith("java"):
            import org.python.core as PyCore #@UnresolvedImport
            ss = PyCore.PySystemState()
            # Note: Py.setSystemState() affects only the current thread.
            PyCore.Py.setSystemState(ss)

        self.OnRun()

    def OnRun(self):
        raise NotImplementedError('Should be reimplemented by: %s' % self.__class__)

    def doKillPydevThread(self):
        #that was not working very well because jython gave some socket errors
        self.killReceived = True

    def stop(self):
        _Thread_stop(self)

    def stopTrace(self):
        if self.dontTraceMe:
            pydevd_tracing.SetTrace(None) # no debugging on this thread


#=======================================================================================================================
# ReaderThread
#=======================================================================================================================
class ReaderThread(PyDBDaemonThread):
    """ reader thread reads and dispatches commands in an infinite loop """

    def __init__(self, sock):
        PyDBDaemonThread.__init__(self)
        self.sock = sock
        self.setName("pydevd.Reader")


    def doKillPydevThread(self):
        #We must close the socket so that it doesn't stay halted there.
        self.killReceived = True
        try:
            self.sock.shutdown(SHUT_RD) #shotdown the socket for read
        except:
            #just ignore that
            pass

    def OnRun(self):
        self.stopTrace()
        buffer = ""
        try:

            while not self.killReceived:
                try:
                    r = self.sock.recv(1024)
                except:
                    if not self.killReceived:
                        self.handleExcept()
                    return #Finished communication.
                if IS_PY3K:
                    r = r.decode('utf-8')

                buffer += r
                if DebugInfoHolder.DEBUG_RECORD_SOCKET_READS:
                    pydev_log.debug('received >>%s<<\n' % (buffer,))

                if len(buffer) == 0:
                    self.handleExcept()
                    break
                while buffer.find('\n') != -1:
                    command, buffer = buffer.split('\n', 1)
                    pydev_log.debug('Received command: >>%s<<\n' % (command,))
                    args = command.split('\t', 2)
                    try:
                        self.processCommand(int(args[0]), int(args[1]), args[2])
                    except:
                        traceback.print_exc()
                        sys.stderr.write("Can't process net command: %s\n" % command)
                        sys.stderr.flush()

        except:
            traceback.print_exc()
            self.handleExcept()


    def handleExcept(self):
        GlobalDebuggerHolder.globalDbg.FinishDebuggingSession()

    def processCommand(self, cmd_id, seq, text):
        GlobalDebuggerHolder.globalDbg.processNetCommand(cmd_id, seq, text)


#----------------------------------------------------------------------------------- SOCKET UTILITIES - WRITER
#=======================================================================================================================
# WriterThread
#=======================================================================================================================
class WriterThread(PyDBDaemonThread):
    """ writer thread writes out the commands in an infinite loop """
    def __init__(self, sock):
        PyDBDaemonThread.__init__(self)
        self.setDaemon(False)  #writer isn't daemon to be able to deliver all messages after main thread terminated
        self.sock = sock
        self.setName("pydevd.Writer")
        self.cmdQueue = _queue.Queue()
        if pydevd_vm_type.GetVmType() == 'python':
            self.timeout = 0
        else:
            self.timeout = 0.1

    def addCommand(self, cmd):
        """ cmd is NetCommand """
        if not self.killReceived: #we don't take new data after everybody die
            self.cmdQueue.put(cmd)

    def OnRun(self):
        """ just loop and write responses """

        self.stopTrace()
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
                            self.stop() #mark thread as stopped to unblock joined threads for sure (they can hang otherwise)

                            return #break if queue is empty and killReceived
                        else:
                            continue
                except:
                    #PydevdLog(0, 'Finishing debug communication...(1)')
                    #when liberating the thread here, we could have errors because we were shutting down
                    #but the thread was still not liberated
                    return
                out = cmd.getOutgoing()

                if DebugInfoHolder.DEBUG_TRACE_LEVEL >= 1:
                    out_message = 'sending cmd: '
                    out_message += ID_TO_MEANING.get(out[:3], 'UNKNOWN')
                    out_message += ' '
                    out_message += out
                    try:
                        sys.stderr.write('%s\n' % (out_message,))
                    except:
                        pass

                if IS_PY3K:
                    out = bytearray(out, 'utf-8')
                self.sock.send(out) #TODO: this does not guarantee that all message are sent (and jython does not have a send all)
                if cmd.id == CMD_EXIT:
                    break
                if time is None:
                    break #interpreter shutdown
                time.sleep(self.timeout)
        except Exception:
            GlobalDebuggerHolder.globalDbg.FinishDebuggingSession()
            if DebugInfoHolder.DEBUG_TRACE_LEVEL >= 0:
                traceback.print_exc()




#--------------------------------------------------- CREATING THE SOCKET THREADS

#=======================================================================================================================
# StartServer
#=======================================================================================================================
def StartServer(port):
    """ binds to a port, waits for the debugger to connect """
    s = socket(AF_INET, SOCK_STREAM)
    s.bind(('', port))
    s.listen(1)
    newSock, _addr = s.accept()
    return newSock

#=======================================================================================================================
# StartClient
#=======================================================================================================================
def StartClient(host, port):
    """ connects to a host/port """
    PydevdLog(1, "Connecting to ", host, ":", str(port))

    s = socket(AF_INET, SOCK_STREAM)

    MAX_TRIES = 3
    i = 0
    while i<MAX_TRIES:
        try:
            s.connect((host, port))
        except:
            i+=1
            time.sleep(0.2)
            continue
        PydevdLog(1, "Connected.")
        return s

    sys.stderr.write("Could not connect to %s: %s\n" % (host, port))
    sys.stderr.flush()
    traceback.print_exc()
    sys.exit(1) #TODO: is it safe?



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

    def __init__(self, id, seq, text):
        """ smart handling of paramaters
        if sequence is 0, new sequence will be generated
        if text has carriage returns they'll be replaced"""
        self.id = id
        if (seq == 0): seq = self.getNextSeq()
        self.seq = seq
        self.text = text
        self.outgoing = self.makeMessage(id, seq, text)

    def getNextSeq(self):
        """ returns next sequence number """
        NetCommand.next_seq += 2
        return NetCommand.next_seq

    def getOutgoing(self):
        """ returns the outgoing message"""
        return self.outgoing

    def makeMessage(self, cmd, seq, payload):
        encoded = quote(to_string(payload), '/<>_=" \t')
        return str(cmd) + '\t' + str(seq) + '\t' + encoded + "\n"

#=======================================================================================================================
# NetCommandFactory
#=======================================================================================================================
class NetCommandFactory:

    def __init_(self):
        self.next_seq = 0

    def threadToXML(self, thread):
        """ thread information as XML """
        name = pydevd_vars.makeValidXmlValue(thread.getName())
        cmdText = '<thread name="%s" id="%s" />' % (quote(name), GetThreadId(thread))
        return cmdText

    def makeErrorMessage(self, seq, text):
        cmd = NetCommand(CMD_ERROR, seq, text)
        if DebugInfoHolder.DEBUG_TRACE_LEVEL > 2:
            sys.stderr.write("Error: %s" % (text,))
        return cmd

    def makeThreadCreatedMessage(self, thread):
        cmdText = "<xml>" + self.threadToXML(thread) + "</xml>"
        return NetCommand(CMD_THREAD_CREATE, 0, cmdText)

    def makeListThreadsMessage(self, seq):
        """ returns thread listing as XML """
        try:
            t = threading.enumerate()
            cmdText = "<xml>"
            for i in t:
                if t.isAlive():
                    cmdText += self.threadToXML(i)
            cmdText += "</xml>"
            return NetCommand(CMD_RETURN, seq, cmdText)
        except:
            return self.makeErrorMessage(seq, GetExceptionTracebackStr())

    def makeVariableChangedMessage(self, seq, payload):
        # notify debugger that value was changed successfully
        return NetCommand(CMD_RETURN, seq, payload)

    def makeIoMessage(self, v, ctx, dbg=None):
        '''
        @param v: the message to pass to the debug server
        @param ctx: 1 for stdio 2 for stderr
        @param dbg: If not none, add to the writer
        '''

        try:
            if len(v) > MAX_IO_MSG_SIZE:
                v = v[0:MAX_IO_MSG_SIZE]
                v += '...'

            v = pydevd_vars.makeValidXmlValue(quote(v, '/>_= \t'))
            net = NetCommand(str(CMD_WRITE_TO_CONSOLE), 0, '<xml><io s="%s" ctx="%s"/></xml>' % (v, ctx))
        except:
            net = self.makeErrorMessage(0, GetExceptionTracebackStr())

        if dbg:
            dbg.writer.addCommand(net)

        return net

    def makeVersionMessage(self, seq):
        try:
            return NetCommand(CMD_VERSION, seq, VERSION_STRING)
        except:
            return self.makeErrorMessage(seq, GetExceptionTracebackStr())

    def makeThreadKilledMessage(self, id):
        try:
            return NetCommand(CMD_THREAD_KILL, 0, str(id))
        except:
            return self.makeErrorMessage(0, GetExceptionTracebackStr())

    def makeThreadSuspendMessage(self, thread_id, frame, stop_reason, message):

        """ <xml>
            <thread id="id" stop_reason="reason">
                    <frame id="id" name="functionName " file="file" line="line">
                    <var variable stuffff....
                </frame>
            </thread>
           """
        try:
            cmdTextList = ["<xml>"]

            if message:
                message = pydevd_vars.makeValidXmlValue(str(message))

            cmdTextList.append('<thread id="%s" stop_reason="%s" message="%s">' % (thread_id, stop_reason, message))

            curFrame = frame
            try:
                while curFrame:
                    #print cmdText
                    myId = str(id(curFrame))
                    #print "id is ", myId

                    if curFrame.f_code is None:
                        break #Iron Python sometimes does not have it!

                    myName = curFrame.f_code.co_name #method name (if in method) or ? if global
                    if myName is None:
                        break #Iron Python sometimes does not have it!

                    #print "name is ", myName

                    filename, base = pydevd_file_utils.GetFilenameAndBase(curFrame)

                    myFile = pydevd_file_utils.NormFileToClient(filename)

                    #print "file is ", myFile
                    #myFile = inspect.getsourcefile(curFrame) or inspect.getfile(frame)

                    myLine = str(curFrame.f_lineno)
                    #print "line is ", myLine

                    #the variables are all gotten 'on-demand'
                    #variables = pydevd_vars.frameVarsToXML(curFrame.f_locals)

                    variables = ''
                    cmdTextList.append('<frame id="%s" name="%s" ' % (myId , pydevd_vars.makeValidXmlValue(myName)))
                    cmdTextList.append('file="%s" line="%s">"' % (quote(myFile, '/>_= \t'), myLine))
                    cmdTextList.append(variables)
                    cmdTextList.append("</frame>")
                    curFrame = curFrame.f_back
            except :
                traceback.print_exc()

            cmdTextList.append("</thread></xml>")
            cmdText = ''.join(cmdTextList)
            return NetCommand(CMD_THREAD_SUSPEND, 0, cmdText)
        except:
            return self.makeErrorMessage(0, GetExceptionTracebackStr())

    def makeThreadRunMessage(self, id, reason):
        try:
            return NetCommand(CMD_THREAD_RUN, 0, str(id) + "\t" + str(reason))
        except:
            return self.makeErrorMessage(0, GetExceptionTracebackStr())

    def makeGetVariableMessage(self, seq, payload):
        try:
            return NetCommand(CMD_GET_VARIABLE, seq, payload)
        except Exception:
            return self.makeErrorMessage(seq, GetExceptionTracebackStr())

    def makeGetFrameMessage(self, seq, payload):
        try:
            return NetCommand(CMD_GET_FRAME, seq, payload)
        except Exception:
            return self.makeErrorMessage(seq, GetExceptionTracebackStr())


    def makeEvaluateExpressionMessage(self, seq, payload):
        try:
            return NetCommand(CMD_EVALUATE_EXPRESSION, seq, payload)
        except Exception:
            return self.makeErrorMessage(seq, GetExceptionTracebackStr())

    def makeGetCompletionsMessage(self, seq, payload):
        try:
            return NetCommand(CMD_GET_COMPLETIONS, seq, payload)
        except Exception:
            return self.makeErrorMessage(seq, GetExceptionTracebackStr())

    def makeLoadSourceMessage(self, seq, source, dbg=None):
        try:
            net = NetCommand(CMD_LOAD_SOURCE, seq, '%s' % source)

        except:
            net = self.makeErrorMessage(0, GetExceptionTracebackStr())

        if dbg:
            dbg.writer.addCommand(net)
        return net

    def makeExitMessage(self):
        try:
            net = NetCommand(CMD_EXIT, 0, '')

        except:
            net = self.makeErrorMessage(0, GetExceptionTracebackStr())

        return net

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

    def canBeExecutedBy(self, thread_id):
        '''By default, it must be in the same thread to be executed
        '''
        return self.thread_id == thread_id

    def doIt(self, dbg):
        raise NotImplementedError("you have to override doIt")

#=======================================================================================================================
# InternalTerminateThread
#=======================================================================================================================
class InternalTerminateThread(InternalThreadCommand):
    def __init__(self, thread_id):
        self.thread_id = thread_id

    def doIt(self, dbg):
        PydevdLog(1, "killing ", str(self.thread_id))
        cmd = dbg.cmdFactory.makeThreadKilledMessage(self.thread_id)
        dbg.writer.addCommand(cmd)


#=======================================================================================================================
# InternalRunThread
#=======================================================================================================================
class InternalRunThread(InternalThreadCommand):
    def __init__(self, thread_id):
        self.thread_id = thread_id

    def doIt(self, dbg):
        t = PydevdFindThreadById(self.thread_id)
        if t:
            t.additionalInfo.pydev_step_cmd = None
            t.additionalInfo.pydev_step_stop = None
            t.additionalInfo.pydev_state = STATE_RUN


#=======================================================================================================================
# InternalStepThread
#=======================================================================================================================
class InternalStepThread(InternalThreadCommand):
    def __init__(self, thread_id, cmd_id):
        self.thread_id = thread_id
        self.cmd_id = cmd_id

    def doIt(self, dbg):
        t = PydevdFindThreadById(self.thread_id)
        if t:
            t.additionalInfo.pydev_step_cmd = self.cmd_id
            t.additionalInfo.pydev_state = STATE_RUN

#=======================================================================================================================
# InternalSetNextStatementThread
#=======================================================================================================================
class InternalSetNextStatementThread(InternalThreadCommand):
    def __init__(self, thread_id, cmd_id, line, func_name):
        self.thread_id = thread_id
        self.cmd_id = cmd_id
        self.line = line
        self.func_name = func_name

    def doIt(self, dbg):
        t = PydevdFindThreadById(self.thread_id)
        if t:
            t.additionalInfo.pydev_step_cmd = self.cmd_id
            t.additionalInfo.pydev_next_line = int(self.line)
            t.additionalInfo.pydev_func_name = self.func_name
            t.additionalInfo.pydev_state = STATE_RUN


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

    def doIt(self, dbg):
        """ Converts request into python variable """
        try:
            xml = "<xml>"
            valDict = pydevd_vars.resolveCompoundVariable(self.thread_id, self.frame_id, self.scope, self.attributes)
            if valDict is None:
                valDict = {}

            keys = valDict.keys()
            if hasattr(keys, 'sort'):
                keys.sort(compare_object_attrs) #Python 3.0 does not have it
            else:
                if IS_PY3K:
                    keys = sorted(keys, key=cmp_to_key(compare_object_attrs)) #Jython 2.1 does not have it (and all must be compared as strings).
                else:
                    keys = sorted(keys, cmp=compare_object_attrs) #Jython 2.1 does not have it (and all must be compared as strings).

            for k in keys:
                xml += pydevd_vars.varToXML(valDict[k], to_string(k))

            xml += "</xml>"
            cmd = dbg.cmdFactory.makeGetVariableMessage(self.sequence, xml)
            dbg.writer.addCommand(cmd)
        except Exception:
            cmd = dbg.cmdFactory.makeErrorMessage(self.sequence, "Error resolving variables " + GetExceptionTracebackStr())
            dbg.writer.addCommand(cmd)


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

    def doIt(self, dbg):
        """ Converts request into python variable """
        try:
            result = pydevd_vars.changeAttrExpression(self.thread_id, self.frame_id, self.attr, self.expression)
            xml = "<xml>"
            xml += pydevd_vars.varToXML(result, "")
            xml += "</xml>"
            cmd = dbg.cmdFactory.makeVariableChangedMessage(self.sequence, xml)
            dbg.writer.addCommand(cmd)
        except Exception:
            cmd = dbg.cmdFactory.makeErrorMessage(self.sequence, "Error changing variable attr:%s expression:%s traceback:%s" % (self.attr, self.expression, GetExceptionTracebackStr()))
            dbg.writer.addCommand(cmd)


#=======================================================================================================================
# InternalGetFrame
#=======================================================================================================================
class InternalGetFrame(InternalThreadCommand):
    """ gets the value of a variable """
    def __init__(self, seq, thread_id, frame_id):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id

    def doIt(self, dbg):
        """ Converts request into python variable """
        try:
            frame = pydevd_vars.findFrame(self.thread_id, self.frame_id)
            if frame is not None:
                xml = "<xml>"
                xml += pydevd_vars.frameVarsToXML(frame.f_locals)
                del frame
                xml += "</xml>"
                cmd = dbg.cmdFactory.makeGetFrameMessage(self.sequence, xml)
                dbg.writer.addCommand(cmd)
            else:
                #pydevd_vars.dumpFrames(self.thread_id)
                #don't print this error: frame not found: means that the client is not synchronized (but that's ok)
                cmd = dbg.cmdFactory.makeErrorMessage(self.sequence, "Frame not found: %s from thread: %s" % (self.frame_id, self.thread_id))
                dbg.writer.addCommand(cmd)
        except:
            cmd = dbg.cmdFactory.makeErrorMessage(self.sequence, "Error resolving frame: %s from thread: %s" % (self.frame_id, self.thread_id))
            dbg.writer.addCommand(cmd)


#=======================================================================================================================
# InternalEvaluateExpression
#=======================================================================================================================
class InternalEvaluateExpression(InternalThreadCommand):
    """ gets the value of a variable """

    def __init__(self, seq, thread_id, frame_id, expression, doExec, doTrim):
        self.sequence = seq
        self.thread_id = thread_id
        self.frame_id = frame_id
        self.expression = expression
        self.doExec = doExec
        self.doTrim = doTrim

    def doIt(self, dbg):
        """ Converts request into python variable """
        try:
            result = pydevd_vars.evaluateExpression(self.thread_id, self.frame_id, self.expression, self.doExec)
            xml = "<xml>"
            xml += pydevd_vars.varToXML(result, "", self.doTrim)
            xml += "</xml>"
            cmd = dbg.cmdFactory.makeEvaluateExpressionMessage(self.sequence, xml)
            dbg.writer.addCommand(cmd)
        except:
            exc = GetExceptionTracebackStr()
            sys.stderr.write('%s\n' % (exc,))
            cmd = dbg.cmdFactory.makeErrorMessage(self.sequence, "Error evaluating expression " + exc)
            dbg.writer.addCommand(cmd)

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

    def doIt(self, dbg):
        """ Converts request into python variable """
        pydev_start_new_thread = None
        try:
            try:
                pydev_start_new_thread = thread.start_new_thread

                thread.start_new_thread = thread._original_start_new_thread #don't trace new threads created by console command
                thread.start_new = thread._original_start_new_thread

                result = pydevconsole.consoleExec(self.thread_id, self.frame_id, self.expression)
                xml = "<xml>"
                xml += pydevd_vars.varToXML(result, "")
                xml += "</xml>"
                cmd = dbg.cmdFactory.makeEvaluateExpressionMessage(self.sequence, xml)
                dbg.writer.addCommand(cmd)
            except:
                exc = GetExceptionTracebackStr()
                sys.stderr.write('%s\n' % (exc,))
                cmd = dbg.cmdFactory.makeErrorMessage(self.sequence, "Error evaluating console expression " + exc)
                dbg.writer.addCommand(cmd)
        finally:
            thread.start_new_thread = pydev_start_new_thread
            thread.start_new = pydev_start_new_thread
            sys.stderr.flush()
            sys.stdout.flush()

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


    def doIt(self, dbg):
        """ Converts request into completions """
        try:
            remove_path = None
            try:
                import _completer
            except:
                try:
                    path = os.environ['PYDEV_COMPLETER_PYTHONPATH']
                except :
                    path = os.path.dirname(__file__)
                sys.path.append(path)
                remove_path = path
                try:
                    import _completer
                except :
                    pass

            try:

                frame = pydevd_vars.findFrame(self.thread_id, self.frame_id)
                if frame is not None:

                    #Not using frame.f_globals because of https://sourceforge.net/tracker2/?func=detail&aid=2541355&group_id=85796&atid=577329
                    #(Names not resolved in generator expression in method)
                    #See message: http://mail.python.org/pipermail/python-list/2009-January/526522.html
                    updated_globals = {}
                    updated_globals.update(frame.f_globals)
                    updated_globals.update(frame.f_locals) #locals later because it has precedence over the actual globals
                    locals = frame.f_locals
                else:
                    updated_globals = {}
                    locals = {}


                if pydevconsole.IPYTHON:
                    completions = pydevconsole.get_completions(self.act_tok, self.act_tok, updated_globals, locals)
                else:
                    try:
                        completer = _completer.Completer(updated_globals, None)
                        #list(tuple(name, descr, parameters, type))
                        completions = completer.complete(self.act_tok)
                    except :
                        completions = []


                def makeValid(s):
                    return pydevd_vars.makeValidXmlValue(pydevd_vars.quote(s, '/>_= \t'))

                msg = "<xml>"

                for comp in completions:
                    msg += '<comp p0="%s" p1="%s" p2="%s" p3="%s"/>' % (makeValid(comp[0]), makeValid(comp[1]), makeValid(comp[2]), makeValid(comp[3]),)
                msg += "</xml>"

                cmd = dbg.cmdFactory.makeGetCompletionsMessage(self.sequence, msg)
                dbg.writer.addCommand(cmd)

            finally:
                if remove_path is not None:
                    sys.path.remove(remove_path)

        except:
            exc = GetExceptionTracebackStr()
            sys.stderr.write('%s\n' % (exc,))
            cmd = dbg.cmdFactory.makeErrorMessage(self.sequence, "Error getting completion " + exc)
            dbg.writer.addCommand(cmd)


#=======================================================================================================================
# PydevdFindThreadById
#=======================================================================================================================
def PydevdFindThreadById(thread_id):
    try:
        # there was a deadlock here when I did not remove the tracing function when thread was dead
        threads = threading.enumerate()
        for i in threads:
            if thread_id == GetThreadId(i):
                return i

        sys.stderr.write("Could not find thread %s\n" % thread_id)
        sys.stderr.write("Available: %s\n" % [GetThreadId(t) for t in threads])
        sys.stderr.flush()
    except:
        traceback.print_exc()

    return None

