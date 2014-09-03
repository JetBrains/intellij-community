'''
    The idea is that we record the commands sent to the debugger and reproduce them from this script
    (so, this works as the client, which spawns the debugger as a separate process and communicates
    to it as if it was run from the outside)

    Note that it's a python script but it'll spawn a process to run as jython, ironpython and as python.
'''
SHOW_WRITES_AND_READS = False
SHOW_OTHER_DEBUG_INFO = False
SHOW_STDOUT = False



from pydevd_constants import IS_PY3K
try:
    from thread import start_new_thread
except:
    from _thread import start_new_thread
CMD_SET_PROPERTY_TRACE, CMD_EVALUATE_CONSOLE_EXPRESSION, CMD_RUN_CUSTOM_OPERATION, CMD_ENABLE_DONT_TRACE = 133, 134, 135, 141
PYTHON_EXE = None
IRONPYTHON_EXE = None
JYTHON_JAR_LOCATION = None
JAVA_LOCATION = None


import unittest
import pydev_localhost
port = None

try:
    xrange
except:
    xrange = range

def UpdatePort():
    global port
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind((pydev_localhost.get_localhost(), 0))
    _, port = s.getsockname()
    s.close()

import os
def _get_debugger_test_file(filename):
    try:
        rPath = os.path.realpath  # @UndefinedVariable
    except:
        # jython does not support os.path.realpath
        # realpath is a no-op on systems without islink support
        rPath = os.path.abspath
        
    return os.path.normcase(rPath(os.path.join(os.path.dirname(__file__), filename)))

import pydevd
PYDEVD_FILE = pydevd.__file__

import sys

import subprocess
import socket
import threading
import time
from pydev_imports import quote_plus, quote, unquote_plus


#=======================================================================================================================
# ReaderThread
#=======================================================================================================================
class ReaderThread(threading.Thread):

    def __init__(self, sock):
        threading.Thread.__init__(self)
        self.setDaemon(True)
        self.sock = sock
        self.lastReceived = ''

    def run(self):
        last_printed = None
        try:
            buf = ''
            while True:
                l = self.sock.recv(1024)
                if IS_PY3K:
                    l = l.decode('utf-8')
                buf += l

                if '\n' in buf:
                    self.lastReceived = buf
                    buf = ''

                if SHOW_WRITES_AND_READS:
                    if last_printed != self.lastReceived.strip():
                        last_printed = self.lastReceived.strip()
                        print('Test Reader Thread Received %s' % last_printed)
        except:
            pass  # ok, finished it

    def DoKill(self):
        self.sock.close()

#=======================================================================================================================
# AbstractWriterThread
#=======================================================================================================================
class AbstractWriterThread(threading.Thread):

    def __init__(self):
        threading.Thread.__init__(self)
        self.setDaemon(True)
        self.finishedOk = False
        self._next_breakpoint_id = 0
        self.log = []


    def DoKill(self):
        if hasattr(self, 'readerThread'):
            # if it's not created, it's not there...
            self.readerThread.DoKill()
        self.sock.close()

    def Write(self, s):
        
        last = self.readerThread.lastReceived
        if SHOW_WRITES_AND_READS:
            print('Test Writer Thread Written %s' % (s,))
        msg = s + '\n'
        if IS_PY3K:
            msg = msg.encode('utf-8')
        self.sock.send(msg)
        time.sleep(0.2)

        i = 0
        while last == self.readerThread.lastReceived and i < 10:
            i += 1
            time.sleep(0.1)


    def StartSocket(self):
        if SHOW_WRITES_AND_READS:
            print('StartSocket')

        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(('', port))
        s.listen(1)
        if SHOW_WRITES_AND_READS:
            print('Waiting in socket.accept()')
        newSock, addr = s.accept()
        if SHOW_WRITES_AND_READS:
            print('Test Writer Thread Socket:', newSock, addr)

        readerThread = self.readerThread = ReaderThread(newSock)
        readerThread.start()
        self.sock = newSock

        self._sequence = -1
        # initial command is always the version
        self.WriteVersion()
        self.log.append('StartSocket')

    def NextBreakpointId(self):
        self._next_breakpoint_id += 1
        return self._next_breakpoint_id

    def NextSeq(self):
        self._sequence += 2
        return self._sequence


    def WaitForNewThread(self):
        i = 0
        # wait for hit breakpoint
        while not '<xml><thread name="' in self.readerThread.lastReceived or '<xml><thread name="pydevd.' in self.readerThread.lastReceived:
            i += 1
            time.sleep(1)
            if i >= 15:
                raise AssertionError('After %s seconds, a thread was not created.' % i)

        # we have something like <xml><thread name="MainThread" id="12103472" /></xml>
        splitted = self.readerThread.lastReceived.split('"')
        threadId = splitted[3]
        return threadId

    def WaitForBreakpointHit(self, reason='111', get_line=False):
        '''
            108 is over
            109 is return
            111 is breakpoint
        '''
        self.log.append('Start: WaitForBreakpointHit')
        i = 0
        # wait for hit breakpoint
        last = self.readerThread.lastReceived
        while not ('stop_reason="%s"' % reason) in last:
            i += 1
            time.sleep(1)
            last = self.readerThread.lastReceived
            if i >= 10:
                raise AssertionError('After %s seconds, a break with reason: %s was not hit. Found: %s' % \
                    (i, reason, last))

        # we have something like <xml><thread id="12152656" stop_reason="111"><frame id="12453120" ...
        splitted = last.split('"')
        threadId = splitted[1]
        frameId = splitted[7]
        if get_line:
            self.log.append('End(0): WaitForBreakpointHit')
            return threadId, frameId, int(splitted[13])

        self.log.append('End(1): WaitForBreakpointHit')
        return threadId, frameId

    def WaitForCustomOperation(self, expected):
        i = 0
        # wait for custom operation response, the response is double encoded
        expectedEncoded = quote(quote_plus(expected))
        while not expectedEncoded in self.readerThread.lastReceived:
            i += 1
            time.sleep(1)
            if i >= 10:
                raise AssertionError('After %s seconds, the custom operation not received. Last found:\n%s\nExpected (encoded)\n%s' % 
                    (i, self.readerThread.lastReceived, expectedEncoded))

        return True

    def WaitForEvaluation(self, expected):
        return self._WaitFor(expected, 'the expected evaluation was not found')


    def WaitForVars(self, expected):
        i = 0
        # wait for hit breakpoint
        while not expected in self.readerThread.lastReceived:
            i += 1
            time.sleep(1)
            if i >= 10:
                raise AssertionError('After %s seconds, the vars were not found. Last found:\n%s' % 
                    (i, self.readerThread.lastReceived))

        return True

    def WaitForVar(self, expected):
        self._WaitFor(expected, 'the var was not found')
        
    def _WaitFor(self, expected, error_msg):
        '''
        :param expected:
            If a list we'll work with any of the choices.
        '''
        if not isinstance(expected, (list, tuple)):
            expected = [expected]
            
        i = 0
        found = False
        while not found:
            last = self.readerThread.lastReceived
            for e in expected:
                if e in last:
                    found = True
                    break
                
            last = unquote_plus(last)
            for e in expected:
                if e in last:
                    found = True
                    break

            if found:
                break
                        
            i += 1
            time.sleep(1)
            if i >= 10:
                raise AssertionError('After %s seconds, %s. Last found:\n%s' % 
                    (i, error_msg, last))

        return True

    def WaitForMultipleVars(self, expected_vars):
        i = 0
        # wait for hit breakpoint
        while True:
            for expected in expected_vars:
                if expected not in self.readerThread.lastReceived:
                    break  # Break out of loop (and don't get to else)
            else:
                return True

            i += 1
            time.sleep(1)
            if i >= 10:
                raise AssertionError('After %s seconds, the vars were not found. Last found:\n%s' % 
                    (i, self.readerThread.lastReceived))

        return True

    def WriteMakeInitialRun(self):
        self.Write("101\t%s\t" % self.NextSeq())
        self.log.append('WriteMakeInitialRun')

    def WriteVersion(self):
        self.Write("501\t%s\t1.0\tWINDOWS\tID" % self.NextSeq())

    def WriteAddBreakpoint(self, line, func):
        '''
            @param line: starts at 1
        '''
        breakpoint_id = self.NextBreakpointId()
        self.Write("111\t%s\t%s\t%s\t%s\t%s\t%s\tNone\tNone" % (self.NextSeq(), breakpoint_id, 'python-line', self.TEST_FILE, line, func))
        self.log.append('WriteAddBreakpoint: %s line: %s func: %s' % (breakpoint_id, line, func))
        return breakpoint_id

    def WriteRemoveBreakpoint(self, breakpoint_id):
        self.Write("112\t%s\t%s\t%s\t%s" % (self.NextSeq(), 'python-line', self.TEST_FILE, breakpoint_id))

    def WriteChangeVariable(self, thread_id, frame_id, varname, value):
        self.Write("117\t%s\t%s\t%s\t%s\t%s\t%s" % (self.NextSeq(), thread_id, frame_id, 'FRAME', varname, value))

    def WriteGetFrame(self, threadId, frameId):
        self.Write("114\t%s\t%s\t%s\tFRAME" % (self.NextSeq(), threadId, frameId))
        self.log.append('WriteGetFrame')

    def WriteGetVariable(self, threadId, frameId, var_attrs):
        self.Write("110\t%s\t%s\t%s\tFRAME\t%s" % (self.NextSeq(), threadId, frameId, var_attrs))

    def WriteStepOver(self, threadId):
        self.Write("108\t%s\t%s" % (self.NextSeq(), threadId,))

    def WriteStepIn(self, threadId):
        self.Write("107\t%s\t%s" % (self.NextSeq(), threadId,))

    def WriteStepReturn(self, threadId):
        self.Write("109\t%s\t%s" % (self.NextSeq(), threadId,))

    def WriteSuspendThread(self, threadId):
        self.Write("105\t%s\t%s" % (self.NextSeq(), threadId,))

    def WriteRunThread(self, threadId):
        self.log.append('WriteRunThread')
        self.Write("106\t%s\t%s" % (self.NextSeq(), threadId,))

    def WriteKillThread(self, threadId):
        self.Write("104\t%s\t%s" % (self.NextSeq(), threadId,))

    def WriteDebugConsoleExpression(self, locator):
        self.Write("%s\t%s\t%s" % (CMD_EVALUATE_CONSOLE_EXPRESSION, self.NextSeq(), locator))

    def WriteCustomOperation(self, locator, style, codeOrFile, operation_fn_name):
        self.Write("%s\t%s\t%s||%s\t%s\t%s" % (CMD_RUN_CUSTOM_OPERATION, self.NextSeq(), locator, style, codeOrFile, operation_fn_name))
        
    def WriteEvaluateExpression(self, locator, expression):
        self.Write("113\t%s\t%s\t%s\t1" % (self.NextSeq(), locator, expression))

    def WriteEnableDontTrace(self, enable):
        if enable:
            enable = 'true'
        else:
            enable = 'false'
        self.Write("%s\t%s\t%s" % (CMD_ENABLE_DONT_TRACE, self.NextSeq(), enable))


#=======================================================================================================================
# WriterThreadCase19 - [Test Case]: Evaluate '__' attributes
#======================================================================================================================
class WriterThreadCase19(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case19.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(8, None)
        self.WriteMakeInitialRun()

        threadId, frameId, line = self.WaitForBreakpointHit('111', True)

        assert line == 8, 'Expected return to be in line 8, was: %s' % line
        
        self.WriteEvaluateExpression('%s\t%s\t%s' % (threadId, frameId, 'LOCAL'), 'a.__var')
        self.WaitForEvaluation('<var name="a.__var" type="int" value="int')
        self.WriteRunThread(threadId)

        
        self.finishedOk = True


#=======================================================================================================================
# WriterThreadCase18 - [Test Case]: change local variable
#======================================================================================================================
class WriterThreadCase18(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case18.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(5, 'm2')
        self.WriteMakeInitialRun()

        thread_id, frame_id, line = self.WaitForBreakpointHit('111', True)
        assert line == 5, 'Expected return to be in line 2, was: %s' % line

        self.WriteChangeVariable(thread_id, frame_id, 'a', '40')
        self.WriteRunThread(thread_id)
        
        self.finishedOk = True

#=======================================================================================================================
# WriterThreadCase17 - [Test Case]: dont trace
#======================================================================================================================
class WriterThreadCase17(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case17.py')

    def run(self):
        self.StartSocket()
        self.WriteEnableDontTrace(True)
        self.WriteAddBreakpoint(27, 'main')
        self.WriteAddBreakpoint(29, 'main')
        self.WriteAddBreakpoint(31, 'main')
        self.WriteAddBreakpoint(33, 'main')
        self.WriteMakeInitialRun()

        for i in range(4):
            threadId, frameId, line = self.WaitForBreakpointHit('111', True)
    
            self.WriteStepIn(threadId)
            threadId, frameId, line = self.WaitForBreakpointHit('107', True)
            # Should Skip step into properties setter
            assert line == 2, 'Expected return to be in line 2, was: %s' % line
            self.WriteRunThread(threadId)

        
        self.finishedOk = True

#=======================================================================================================================
# WriterThreadCase16 - [Test Case]: numpy.ndarray resolver
#======================================================================================================================
class WriterThreadCase16(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case16.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(9, 'main')
        self.WriteMakeInitialRun()

        threadId, frameId, line = self.WaitForBreakpointHit('111', True)

        # In this test we check that the three arrays of different shapes, sizes and types
        # are all resolved properly as ndarrays.

        # First pass check is that we have all three expected variables defined
        self.WriteGetFrame(threadId, frameId)
        self.WaitForVars('<var name="smallarray" type="ndarray" value="ndarray%253A %255B  0.%252B1.j   1.%252B1.j   2.%252B1.j   3.%252B1.j   4.%252B1.j   5.%252B1.j   6.%252B1.j   7.%252B1.j%250A   8.%252B1.j   9.%252B1.j  10.%252B1.j  11.%252B1.j  12.%252B1.j  13.%252B1.j  14.%252B1.j  15.%252B1.j%250A  16.%252B1.j  17.%252B1.j  18.%252B1.j  19.%252B1.j  20.%252B1.j  21.%252B1.j  22.%252B1.j  23.%252B1.j%250A  24.%252B1.j  25.%252B1.j  26.%252B1.j  27.%252B1.j  28.%252B1.j  29.%252B1.j  30.%252B1.j  31.%252B1.j%250A  32.%252B1.j  33.%252B1.j  34.%252B1.j  35.%252B1.j  36.%252B1.j  37.%252B1.j  38.%252B1.j  39.%252B1.j%250A  40.%252B1.j  41.%252B1.j  42.%252B1.j  43.%252B1.j  44.%252B1.j  45.%252B1.j  46.%252B1.j  47.%252B1.j%250A  48.%252B1.j  49.%252B1.j  50.%252B1.j  51.%252B1.j  52.%252B1.j  53.%252B1.j  54.%252B1.j  55.%252B1.j%250A  56.%252B1.j  57.%252B1.j  58.%252B1.j  59.%252B1.j  60.%252B1.j  61.%252B1.j  62.%252B1.j  63.%252B1.j%250A  64.%252B1.j  65.%252B1.j  66.%252B1.j  67.%252B1.j  68.%252B1.j  69.%252B1.j  70.%252B1.j  71.%252B1.j%250A  72.%252B1.j  73.%252B1.j  74.%252B1.j  75.%252B1.j  76.%252B1.j  77.%252B1.j  78.%252B1.j  79.%252B1.j%250A  80.%252B1.j  81.%252B1.j  82.%252B1.j  83.%252B1.j  84.%252B1.j  85.%252B1.j  86.%252B1.j  87.%252B1.j%250A  88.%252B1.j  89.%252B1.j  90.%252B1.j  91.%252B1.j  92.%252B1.j  93.%252B1.j  94.%252B1.j  95.%252B1.j%250A  96.%252B1.j  97.%252B1.j  98.%252B1.j  99.%252B1.j%255D" isContainer="True" />')
        self.WaitForVars('<var name="bigarray" type="ndarray" value="ndarray%253A %255B%255B    0     1     2 ...%252C  9997  9998  9999%255D%250A %255B10000 10001 10002 ...%252C 19997 19998 19999%255D%250A %255B20000 20001 20002 ...%252C 29997 29998 29999%255D%250A ...%252C %250A %255B70000 70001 70002 ...%252C 79997 79998 79999%255D%250A %255B80000 80001 80002 ...%252C 89997 89998 89999%255D%250A %255B90000 90001 90002 ...%252C 99997 99998 99999%255D%255D" isContainer="True" />')
        self.WaitForVars('<var name="hugearray" type="ndarray" value="ndarray%253A %255B      0       1       2 ...%252C 9999997 9999998 9999999%255D" isContainer="True" />')

        # For each variable, check each of the resolved (meta data) attributes...
        self.WriteGetVariable(threadId, frameId, 'smallarray')
        self.WaitForVar('<var name="min" type="complex128"')
        self.WaitForVar('<var name="max" type="complex128"')
        self.WaitForVar('<var name="shape" type="tuple"')
        self.WaitForVar('<var name="dtype" type="dtype"')
        self.WaitForVar('<var name="size" type="int"')
        # ...and check that the internals are resolved properly
        self.WriteGetVariable(threadId, frameId, 'smallarray\t__internals__')
        self.WaitForVar('<var name="%27size%27')

        self.WriteGetVariable(threadId, frameId, 'bigarray')
        self.WaitForVar([
            '<var name="min" type="int64" value="int64%253A 0" />', 
            '<var name="min" type="int64" value="int64%3A 0" />', 
            '<var name="size" type="int" value="int%3A 100000" />',
        ])
        self.WaitForVar([
            '<var name="max" type="int64" value="int64%253A 99999" />', 
            '<var name="max" type="int32" value="int32%253A 99999" />',
            '<var name="max" type="int64" value="int64%3A 99999"'
        ])
        self.WaitForVar('<var name="shape" type="tuple"')
        self.WaitForVar('<var name="dtype" type="dtype"')
        self.WaitForVar('<var name="size" type="int"')
        self.WriteGetVariable(threadId, frameId, 'bigarray\t__internals__')
        self.WaitForVar('<var name="%27size%27')

        # this one is different because it crosses the magic threshold where we don't calculate
        # the min/max
        self.WriteGetVariable(threadId, frameId, 'hugearray')
        self.WaitForVar([
            '<var name="min" type="str" value="str%253A ndarray too big%252C calculating min would slow down debugging" />',
            '<var name="min" type="str" value="str%3A ndarray too big%252C calculating min would slow down debugging" />',
        ])
        self.WaitForVar([
            '<var name="max" type="str" value="str%253A ndarray too big%252C calculating max would slow down debugging" />',
            '<var name="max" type="str" value="str%3A ndarray too big%252C calculating max would slow down debugging" />',
        ])
        self.WaitForVar('<var name="shape" type="tuple"')
        self.WaitForVar('<var name="dtype" type="dtype"')
        self.WaitForVar('<var name="size" type="int"')
        self.WriteGetVariable(threadId, frameId, 'hugearray\t__internals__')
        self.WaitForVar('<var name="%27size%27')

        self.WriteRunThread(threadId)
        self.finishedOk = True


#=======================================================================================================================
# WriterThreadCase15 - [Test Case]: Custom Commands
#======================================================================================================================
class WriterThreadCase15(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case15.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(22, 'main')
        self.WriteMakeInitialRun()

        threadId, frameId, line = self.WaitForBreakpointHit('111', True)

        # Access some variable
        self.WriteCustomOperation("%s\t%s\tEXPRESSION\tcarObj.color" % (threadId, frameId), "EXEC", "f=lambda x: 'val=%s' % x", "f")
        self.WaitForCustomOperation('val=Black')
        assert 7 == self._sequence, 'Expected 7. Had: %s' % self._sequence

        self.WriteCustomOperation("%s\t%s\tEXPRESSION\tcarObj.color" % (threadId, frameId), "EXECFILE", _get_debugger_test_file('_debugger_case15_execfile.py'), "f")
        self.WaitForCustomOperation('val=Black')
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.WriteRunThread(threadId)
        self.finishedOk = True



#=======================================================================================================================
# WriterThreadCase14 - [Test Case]: Interactive Debug Console
#======================================================================================================================
class WriterThreadCase14(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case14.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(22, 'main')
        self.WriteMakeInitialRun()

        threadId, frameId, line = self.WaitForBreakpointHit('111', True)
        assert threadId, '%s not valid.' % threadId
        assert frameId, '%s not valid.' % frameId

        # Access some variable
        self.WriteDebugConsoleExpression("%s\t%s\tEVALUATE\tcarObj.color" % (threadId, frameId))
        self.WaitForVar(['<more>False</more>', '%27Black%27'])
        assert 7 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        # Change some variable
        self.WriteDebugConsoleExpression("%s\t%s\tEVALUATE\tcarObj.color='Red'" % (threadId, frameId))
        self.WriteDebugConsoleExpression("%s\t%s\tEVALUATE\tcarObj.color" % (threadId, frameId))
        self.WaitForVar(['<more>False</more>', '%27Red%27'])
        assert 11 == self._sequence, 'Expected 13. Had: %s' % self._sequence

        # Iterate some loop
        self.WriteDebugConsoleExpression("%s\t%s\tEVALUATE\tfor i in range(3):" % (threadId, frameId))
        self.WaitForVar(['<xml><more>True</more></xml>', '<xml><more>1</more></xml>'])
        self.WriteDebugConsoleExpression("%s\t%s\tEVALUATE\t    print(i)" % (threadId, frameId))
        self.WriteDebugConsoleExpression("%s\t%s\tEVALUATE\t" % (threadId, frameId))
        self.WaitForVar(
            [
                '<xml><more>False</more><output message="0"></output><output message="1"></output><output message="2"></output></xml>',
                '<xml><more>0</more><output message="0"></output><output message="1"></output><output message="2"></output></xml>'
            ]
            )
        assert 17 == self._sequence, 'Expected 19. Had: %s' % self._sequence

        self.WriteRunThread(threadId)
        self.finishedOk = True


#=======================================================================================================================
# WriterThreadCase13
#======================================================================================================================
class WriterThreadCase13(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case13.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(35, 'main')
        self.Write("%s\t%s\t%s" % (CMD_SET_PROPERTY_TRACE, self.NextSeq(), "true;false;false;true"))
        self.WriteMakeInitialRun()
        threadId, frameId, line = self.WaitForBreakpointHit('111', True)

        self.WriteGetFrame(threadId, frameId)

        self.WriteStepIn(threadId)
        threadId, frameId, line = self.WaitForBreakpointHit('107', True)
        # Should go inside setter method
        assert line == 25, 'Expected return to be in line 25, was: %s' % line

        self.WriteStepIn(threadId)
        threadId, frameId, line = self.WaitForBreakpointHit('107', True)

        self.WriteStepIn(threadId)
        threadId, frameId, line = self.WaitForBreakpointHit('107', True)
        # Should go inside getter method
        assert line == 21, 'Expected return to be in line 21, was: %s' % line

        self.WriteStepIn(threadId)
        threadId, frameId, line = self.WaitForBreakpointHit('107', True)

        # Disable property tracing
        self.Write("%s\t%s\t%s" % (CMD_SET_PROPERTY_TRACE, self.NextSeq(), "true;true;true;true"))
        self.WriteStepIn(threadId)
        threadId, frameId, line = self.WaitForBreakpointHit('107', True)
        # Should Skip step into properties setter
        assert line == 39, 'Expected return to be in line 39, was: %s' % line

        # Enable property tracing
        self.Write("%s\t%s\t%s" % (CMD_SET_PROPERTY_TRACE, self.NextSeq(), "true;false;false;true"))
        self.WriteStepIn(threadId)
        threadId, frameId, line = self.WaitForBreakpointHit('107', True)
        # Should go inside getter method
        assert line == 8, 'Expected return to be in line 8, was: %s' % line

        self.WriteRunThread(threadId)

        self.finishedOk = True

#=======================================================================================================================
# WriterThreadCase12
#======================================================================================================================
class WriterThreadCase12(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(2, '')  # Should not be hit: setting empty function (not None) should only hit global.
        self.WriteAddBreakpoint(6, 'Method1a')
        self.WriteAddBreakpoint(11, 'Method2')
        self.WriteMakeInitialRun()

        threadId, frameId, line = self.WaitForBreakpointHit('111', True)

        assert line == 11, 'Expected return to be in line 11, was: %s' % line

        self.WriteStepReturn(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('111', True)  # not a return (it stopped in the other breakpoint)

        assert line == 6, 'Expected return to be in line 6, was: %s' % line

        self.WriteRunThread(threadId)

        assert 13 == self._sequence, 'Expected 13. Had: %s' % self._sequence

        self.finishedOk = True



#=======================================================================================================================
# WriterThreadCase11
#======================================================================================================================
class WriterThreadCase11(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(2, 'Method1')
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit('111')

        self.WriteStepOver(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('108', True)

        assert line == 3, 'Expected return to be in line 3, was: %s' % line

        self.WriteStepOver(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('108', True)

        assert line == 11, 'Expected return to be in line 11, was: %s' % line

        self.WriteStepOver(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('108', True)

        assert line == 12, 'Expected return to be in line 12, was: %s' % line

        self.WriteRunThread(threadId)

        assert 13 == self._sequence, 'Expected 13. Had: %s' % self._sequence

        self.finishedOk = True




#=======================================================================================================================
# WriterThreadCase10
#======================================================================================================================
class WriterThreadCase10(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(2, 'None')  # None or Method should make hit.
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit('111')

        self.WriteStepReturn(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('109', True)

        assert line == 11, 'Expected return to be in line 11, was: %s' % line

        self.WriteStepOver(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('108', True)

        assert line == 12, 'Expected return to be in line 12, was: %s' % line

        self.WriteRunThread(threadId)

        assert 11 == self._sequence, 'Expected 11. Had: %s' % self._sequence

        self.finishedOk = True



#=======================================================================================================================
# WriterThreadCase9
#======================================================================================================================
class WriterThreadCase9(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case89.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(10, 'Method3')
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit('111')

        self.WriteStepOver(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('108', True)

        assert line == 11, 'Expected return to be in line 11, was: %s' % line

        self.WriteStepOver(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('108', True)

        assert line == 12, 'Expected return to be in line 12, was: %s' % line

        self.WriteRunThread(threadId)

        assert 11 == self._sequence, 'Expected 11. Had: %s' % self._sequence

        self.finishedOk = True


#=======================================================================================================================
# WriterThreadCase8
#======================================================================================================================
class WriterThreadCase8(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case89.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(10, 'Method3')
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit('111')

        self.WriteStepReturn(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('109', True)

        assert line == 15, 'Expected return to be in line 15, was: %s' % line

        self.WriteRunThread(threadId)

        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.finishedOk = True




#=======================================================================================================================
# WriterThreadCase7
#======================================================================================================================
class WriterThreadCase7(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case7.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(2, 'Call')
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit('111')

        self.WriteGetFrame(threadId, frameId)

        self.WaitForVars('<xml></xml>')  # no vars at this point

        self.WriteStepOver(threadId)

        self.WriteGetFrame(threadId, frameId)

        self.WaitForVars('<xml><var name="variable_for_test_1" type="int" value="int%253A 10" />%0A</xml>')

        self.WriteStepOver(threadId)

        self.WriteGetFrame(threadId, frameId)

        self.WaitForVars('<xml><var name="variable_for_test_1" type="int" value="int%253A 10" />%0A<var name="variable_for_test_2" type="int" value="int%253A 20" />%0A</xml>')

        self.WriteRunThread(threadId)

        assert 17 == self._sequence, 'Expected 17. Had: %s' % self._sequence

        self.finishedOk = True



#=======================================================================================================================
# WriterThreadCase6
#=======================================================================================================================
class WriterThreadCase6(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case56.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(2, 'Call2')
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit()

        self.WriteGetFrame(threadId, frameId)

        self.WriteStepReturn(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('109', True)

        assert line == 8, 'Expecting it to go to line 8. Went to: %s' % line

        self.WriteStepIn(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('107', True)

        # goes to line 4 in jython (function declaration line)
        assert line in (4, 5), 'Expecting it to go to line 4 or 5. Went to: %s' % line

        self.WriteRunThread(threadId)

        assert 13 == self._sequence, 'Expected 15. Had: %s' % self._sequence

        self.finishedOk = True

#=======================================================================================================================
# WriterThreadCase5
#=======================================================================================================================
class WriterThreadCase5(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case56.py')

    def run(self):
        self.StartSocket()
        breakpoint_id = self.WriteAddBreakpoint(2, 'Call2')
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit()

        self.WriteGetFrame(threadId, frameId)

        self.WriteRemoveBreakpoint(breakpoint_id)

        self.WriteStepReturn(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('109', True)

        assert line == 8, 'Expecting it to go to line 8. Went to: %s' % line

        self.WriteStepIn(threadId)

        threadId, frameId, line = self.WaitForBreakpointHit('107', True)

        # goes to line 4 in jython (function declaration line)
        assert line in (4, 5), 'Expecting it to go to line 4 or 5. Went to: %s' % line

        self.WriteRunThread(threadId)

        assert 15 == self._sequence, 'Expected 15. Had: %s' % self._sequence

        self.finishedOk = True


#=======================================================================================================================
# WriterThreadCase4
#=======================================================================================================================
class WriterThreadCase4(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case4.py')

    def run(self):
        self.StartSocket()
        self.WriteMakeInitialRun()

        threadId = self.WaitForNewThread()

        self.WriteSuspendThread(threadId)

        time.sleep(4)  # wait for time enough for the test to finish if it wasn't suspended

        self.WriteRunThread(threadId)

        self.finishedOk = True


#=======================================================================================================================
# WriterThreadCase3
#=======================================================================================================================
class WriterThreadCase3(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case3.py')

    def run(self):
        self.StartSocket()
        self.WriteMakeInitialRun()
        time.sleep(.5)
        breakpoint_id = self.WriteAddBreakpoint(4, '')
        self.WriteAddBreakpoint(5, 'FuncNotAvailable')  # Check that it doesn't get hit in the global when a function is available

        threadId, frameId = self.WaitForBreakpointHit()

        self.WriteGetFrame(threadId, frameId)

        self.WriteRunThread(threadId)

        threadId, frameId = self.WaitForBreakpointHit()

        self.WriteGetFrame(threadId, frameId)

        self.WriteRemoveBreakpoint(breakpoint_id)

        self.WriteRunThread(threadId)

        assert 17 == self._sequence, 'Expected 17. Had: %s' % self._sequence

        self.finishedOk = True

#=======================================================================================================================
# WriterThreadCase2
#=======================================================================================================================
class WriterThreadCase2(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case2.py')

    def run(self):
        self.StartSocket()
        self.WriteAddBreakpoint(3, 'Call4')  # seq = 3
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit()

        self.WriteGetFrame(threadId, frameId)

        self.WriteAddBreakpoint(14, 'Call2')

        self.WriteRunThread(threadId)

        threadId, frameId = self.WaitForBreakpointHit()

        self.WriteGetFrame(threadId, frameId)

        self.WriteRunThread(threadId)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 15 == self._sequence, 'Expected 15. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finishedOk = True

#=======================================================================================================================
# WriterThreadCaseQThread1
#=======================================================================================================================
class WriterThreadCaseQThread1(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case_qthread1.py')

    def run(self):
        self.StartSocket()
        breakpoint_id = self.WriteAddBreakpoint(16, 'run')
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit()

        self.WriteRemoveBreakpoint(breakpoint_id)
        self.WriteRunThread(threadId)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finishedOk = True

#=======================================================================================================================
# WriterThreadCaseQThread2
#=======================================================================================================================
class WriterThreadCaseQThread2(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case_qthread2.py')

    def run(self):
        self.StartSocket()
        breakpoint_id = self.WriteAddBreakpoint(18, 'longRunning')
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit()

        self.WriteRemoveBreakpoint(breakpoint_id)
        self.WriteRunThread(threadId)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finishedOk = True

#=======================================================================================================================
# WriterThreadCaseQThread3
#=======================================================================================================================
class WriterThreadCaseQThread3(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case_qthread3.py')

    def run(self):
        self.StartSocket()
        breakpoint_id = self.WriteAddBreakpoint(19, 'run')
        self.WriteMakeInitialRun()

        threadId, frameId = self.WaitForBreakpointHit()

        self.WriteRemoveBreakpoint(breakpoint_id)
        self.WriteRunThread(threadId)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finishedOk = True

#=======================================================================================================================
# WriterThreadCase1
#=======================================================================================================================
class WriterThreadCase1(AbstractWriterThread):

    TEST_FILE = _get_debugger_test_file('_debugger_case1.py')

    def run(self):
        self.StartSocket()
        
        self.log.append('writing add breakpoint')
        self.WriteAddBreakpoint(6, 'SetUp')
        
        self.log.append('making initial run')
        self.WriteMakeInitialRun()

        self.log.append('waiting for breakpoint hit')
        threadId, frameId = self.WaitForBreakpointHit()

        self.log.append('get frame')
        self.WriteGetFrame(threadId, frameId)

        self.log.append('step over')
        self.WriteStepOver(threadId)

        self.log.append('get frame')
        self.WriteGetFrame(threadId, frameId)

        self.log.append('run thread')
        self.WriteRunThread(threadId)

        self.log.append('asserting')
        try:
            assert 13 == self._sequence, 'Expected 13. Had: %s' % self._sequence
        except:
            self.log.append('assert failed!')
            raise
        self.log.append('asserted')

        self.finishedOk = True

#=======================================================================================================================
# DebuggerBase
#=======================================================================================================================
class DebuggerBase(object):

    def getCommandLine(self):
        raise NotImplementedError

    def CheckCase(self, writerThreadClass):
        UpdatePort()
        writerThread = writerThreadClass()
        writerThread.start()
        time.sleep(1)

        localhost = pydev_localhost.get_localhost()
        args = self.getCommandLine()
        args += [
            PYDEVD_FILE,
            '--DEBUG_RECORD_SOCKET_READS',
            '--client',
            localhost,
            '--port',
            str(port),
            '--file',
            writerThread.TEST_FILE,
        ]

        if SHOW_OTHER_DEBUG_INFO:
            print('executing', ' '.join(args))

        process = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, cwd=os.path.dirname(PYDEVD_FILE))

        stdout = []
        stderr = []

        def read(stream, buffer):
            for line in stream.readlines():
                if IS_PY3K:
                    line = line.decode('utf-8')

                if SHOW_STDOUT:
                    print(line)
                buffer.append(line)
            
        start_new_thread(read, (process.stdout, stdout))
        
        
        if SHOW_OTHER_DEBUG_INFO:
            print('Both processes started')

        # polls can fail (because the process may finish and the thread still not -- so, we give it some more chances to
        # finish successfully).
        check = 0
        while True:
            if process.poll() is not None:
                break
            else:
                if not writerThread.isAlive():
                    check += 1
                    if check == 20:
                        print('Warning: writer thread exited and process still did not.')
                    if check == 100:
                        self.fail_with_message(
                            "The other process should've exited but still didn't (timeout for process to exit).", 
                            stdout, stderr, writerThread
                        )
            time.sleep(.2)
            
            
        poll = process.poll()
        if poll < 0:
            self.fail_with_message(
                "The other process exited with error code: " + str(poll), stdout, stderr, writerThread)


        if stdout is None:
            self.fail_with_message(
                "The other process may still be running -- and didn't give any output.", stdout, stderr, writerThread)

        if 'TEST SUCEEDED' not in ''.join(stdout):
            self.fail_with_message("TEST SUCEEDED not found in stdout.", stdout, stderr, writerThread)

        for i in xrange(100):
            if not writerThread.finishedOk:
                time.sleep(.1)
            
        if not writerThread.finishedOk:
            self.fail_with_message(
                "The thread that was doing the tests didn't finish successfully.", stdout, stderr, writerThread)
            
    def fail_with_message(self, msg, stdout, stderr, writerThread):
        self.fail(msg+
            "\nStdout: \n"+'\n'.join(stdout)+
            "\nStderr:"+'\n'.join(stderr)+
            "\nLog:\n"+'\n'.join(getattr(writerThread, 'log', [])))
        

    def testCase1(self):
        self.CheckCase(WriterThreadCase1)

    def testCase2(self):
        self.CheckCase(WriterThreadCase2)

    def testCase3(self):
        self.CheckCase(WriterThreadCase3)

    def testCase4(self):
        self.CheckCase(WriterThreadCase4)

    def testCase5(self):
        self.CheckCase(WriterThreadCase5)

    def testCase6(self):
        self.CheckCase(WriterThreadCase6)

    def testCase7(self):
        self.CheckCase(WriterThreadCase7)

    def testCase8(self):
        self.CheckCase(WriterThreadCase8)

    def testCase9(self):
        self.CheckCase(WriterThreadCase9)

    def testCase10(self):
        self.CheckCase(WriterThreadCase10)

    def testCase11(self):
        self.CheckCase(WriterThreadCase11)

    def testCase12(self):
        self.CheckCase(WriterThreadCase12)

    def testCase13(self):
        self.CheckCase(WriterThreadCase13)

    def testCase14(self):
        self.CheckCase(WriterThreadCase14)

    def testCase15(self):
        self.CheckCase(WriterThreadCase15)

    def testCase16(self):
        self.CheckCase(WriterThreadCase16)

    def testCase17(self):
        self.CheckCase(WriterThreadCase17)
        
    def testCase18(self):
        self.CheckCase(WriterThreadCase18)
        
    def testCase19(self):
        self.CheckCase(WriterThreadCase19)
        
    def _has_qt(self):
        try:
            from PySide import QtCore
            return True
        except:
            try:
                from PyQt4 import QtCore
                return True
            except:
                pass
        return False

    def testCaseQthread1(self):
        if self._has_qt():
            self.CheckCase(WriterThreadCaseQThread1)

    def testCaseQthread2(self):
        if self._has_qt():
            self.CheckCase(WriterThreadCaseQThread2)

    def testCaseQthread3(self):
        if self._has_qt():
            self.CheckCase(WriterThreadCaseQThread3)


class TestPython(unittest.TestCase, DebuggerBase):
    def getCommandLine(self):
        return [PYTHON_EXE]

class TestJython(unittest.TestCase, DebuggerBase):
    def getCommandLine(self):
        return [
                JAVA_LOCATION,
                '-classpath',
                JYTHON_JAR_LOCATION,
                'org.python.util.jython'
            ]

    # This case requires decorators to work (which are not present on Jython 2.1), so, this test is just removed from the jython run.
    def testCase13(self):
        self.skipTest("Unsupported Decorators")

    # This case requires decorators to work (which are not present on Jython 2.1), so, this test is just removed from the jython run.
    def testCase17(self):
        self.skipTest("Unsupported Decorators")

    def testCase18(self):
        self.skipTest("Unsupported assign to local")

    def testCase16(self):
        self.skipTest("Unsupported numpy")

class TestIronPython(unittest.TestCase, DebuggerBase):
    def getCommandLine(self):
        return [
                IRONPYTHON_EXE,
                '-X:Frames'
            ]

    def testCase3(self):
        self.skipTest("Timing issues") # This test fails once in a while due to timing issues on IronPython, so, skipping it. 
        
    def testCase7(self):
        # This test checks that we start without variables and at each step a new var is created, but on ironpython,
        # the variables exist all at once (with None values), so, we can't test it properly.
        self.skipTest("Different behavior on IronPython") 
        
    def testCase13(self):
        self.skipTest("Unsupported Decorators") # Not sure why it doesn't work on IronPython, but it's not so common, so, leave it be.
        
    def testCase16(self):
        self.skipTest("Unsupported numpy")
        
    def testCase18(self):
        self.skipTest("Unsupported assign to local")


def GetLocationFromLine(line):
    loc = line.split('=')[1].strip()
    if loc.endswith(';'):
        loc = loc[:-1]
    if loc.endswith('"'):
        loc = loc[:-1]
    if loc.startswith('"'):
        loc = loc[1:]
    return loc


def SplitLine(line):
    if '=' not in line:
        return None, None
    var = line.split('=')[0].strip()
    return var, GetLocationFromLine(line)




import platform
sysname = platform.system().lower()
test_dependent = os.path.join('../../../', 'org.python.pydev.core', 'tests', 'org', 'python', 'pydev', 'core', 'TestDependent.' + sysname + '.properties')

if os.path.exists(test_dependent):
    f = open(test_dependent)
    try:
        for line in f.readlines():
            var, loc = SplitLine(line)
            if 'PYTHON_EXE' == var:
                PYTHON_EXE = loc
    
            if 'IRONPYTHON_EXE' == var:
                IRONPYTHON_EXE = loc
    
            if 'JYTHON_JAR_LOCATION' == var:
                JYTHON_JAR_LOCATION = loc
    
            if 'JAVA_LOCATION' == var:
                JAVA_LOCATION = loc
    finally:
        f.close()
else:
    pass

if IRONPYTHON_EXE is None:
    sys.stderr.write('Warning: not running IronPython tests.\n')
    class TestIronPython(unittest.TestCase):
        pass
    
if JAVA_LOCATION is None:
    sys.stderr.write('Warning: not running Jython tests.\n')
    class TestJython(unittest.TestCase):
        pass
    
# if PYTHON_EXE is None:
PYTHON_EXE = sys.executable
    
    
if __name__ == '__main__':
    if False:
        assert PYTHON_EXE, 'PYTHON_EXE not found in %s' % (test_dependent,)
        assert IRONPYTHON_EXE, 'IRONPYTHON_EXE not found in %s' % (test_dependent,)
        assert JYTHON_JAR_LOCATION, 'JYTHON_JAR_LOCATION not found in %s' % (test_dependent,)
        assert JAVA_LOCATION, 'JAVA_LOCATION not found in %s' % (test_dependent,)
        assert os.path.exists(PYTHON_EXE), 'The location: %s is not valid' % (PYTHON_EXE,)
        assert os.path.exists(IRONPYTHON_EXE), 'The location: %s is not valid' % (IRONPYTHON_EXE,)
        assert os.path.exists(JYTHON_JAR_LOCATION), 'The location: %s is not valid' % (JYTHON_JAR_LOCATION,)
        assert os.path.exists(JAVA_LOCATION), 'The location: %s is not valid' % (JAVA_LOCATION,)
    
    if True:
        #try:
        #    os.remove(r'X:\pydev\plugins\org.python.pydev\pysrc\pydevd.pyc')
        #except:
        #    pass
        suite = unittest.TestSuite()
        
#         suite.addTests(unittest.makeSuite(TestJython)) # Note: Jython should be 2.2.1
#           
#         suite.addTests(unittest.makeSuite(TestIronPython))
#         
#         suite.addTests(unittest.makeSuite(TestPython))




#         suite.addTest(TestIronPython('testCase18'))
#         suite.addTest(TestIronPython('testCase17'))
#         suite.addTest(TestIronPython('testCase3'))
#         suite.addTest(TestIronPython('testCase7'))
#         
        suite.addTest(TestPython('testCaseQthread1'))
        suite.addTest(TestPython('testCaseQthread2'))
        suite.addTest(TestPython('testCaseQthread3'))
        
#         suite.addTest(TestPython('testCase4'))


#         suite.addTest(TestJython('testCase1'))
#         suite.addTest(TestPython('testCase2'))
#         unittest.TextTestRunner(verbosity=3).run(suite)
    #     suite.addTest(TestPython('testCase17'))
    #     suite.addTest(TestPython('testCase18'))
    #     suite.addTest(TestPython('testCase19'))
        
        unittest.TextTestRunner(verbosity=3).run(suite)
