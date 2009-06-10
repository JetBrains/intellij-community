"""
popen2.py

Implement popen2 module functionality for Jython.

Note that the popen* methods in this module follow the return value
ordering of the Python popen2.popen* methods:

	fromChild, toChild = popen2.popen2(...)
	fromChild, toChild, errorFromChild = popen2.popen3(...)
	fromChildInclError, toChild = popen2.popen4(...)

The os.popen* methods are more natural as follows:

	toChild, fromChild = os.popen2(...)
	toChild, fromChild, errorFromChild = os.popen3(...)
	toChild, fromChildInclError = os.popen4(...)

The Popen3 and Popen4 classes allow users to poll() or wait() for
child processes to terminate.
"""

import jarray
from java.lang import System
from java.util import Vector
from java.io import BufferedOutputStream
from java.io import BufferedInputStream
from java.io import PipedOutputStream
from java.io import PipedInputStream
from org.python.core import PyFile
from javashell import shellexecute

__all__ = ["popen", "popen2", "popen3", "popen4", "Popen3", "Popen4"]

_active = []

class _ProcessFile:
    """Python file object that returns the process exit status from
    the close method.
    """
    def __init__(self, stream, process, name):
        self._file = PyFile(stream, "'%s'" % name)
        self._process = process

    def __getattr__(self, name):
        return getattr(self._file, name)

    def __repr__(self):
        return `self._file`
        
    def close(self):
        self._file.close()
        return self._process.waitFor() or None

class Popen3:
    """Class representing a child process.  Normally instances are created
    by the factory functions popen2() and popen3()."""

    sts = -1                            # Child not completed yet
    childWaiter = None
    count = 0
    
    def __init__(self, cmd, capturestderr=0, bufsize=-1):
        """The parameter 'cmd' is the shell command to execute in a
        sub-process.  Can be either a sequence of executable
        and arguments, or a shell command.
        The 'capturestderr' flag, if true, specifies that
        the object should capture standard error output of the child process.
        The default is false.  If the 'bufsize' parameter is specified, it
        specifies the size of the I/O buffers to/from the child process.
        """
        self.process = shellexecute( cmd )
        self._tochild = self.process.getOutputStream()
        self._fromchild = self.process.getInputStream()
        if capturestderr:
            self._childerr = self.process.getErrorStream()
        else:
            self._childerr = None
        import threading
        self.childWaiterLock = threading.Lock()

        if bufsize > 0:
            self._tochild = BufferedOutputStream( self._tochild, bufsize )
            self._fromchild = BufferedInputStream( self._fromchild, bufsize )
            if self._childerr:
                self._childerr = BufferedInputStream(
                    self._childerr,
                    bufsize
                    )
                
        self.tochild = PyFile( self._tochild )
        self.fromchild = PyFile( self._fromchild )
        if self._childerr:
            self.childerr = PyFile( self._childerr )

    def _startChildWaiter(self):
        """Start a subthread that waits for the child process to exit."""
        self.childWaiterLock.acquire()
        try:
            if not self.childWaiter:
                import threading
                self.childWaiter = threading.Thread(
                    target=self.wait,
                    name="ChildWaiter %s" % self.process,
                    args=()
                    )
                self.childWaiter.setDaemon( 1 )
                self.childWaiter.start()
        finally:
            self.childWaiterLock.release()

    def poll(self):
        """Return the exit status of the child process if it has finished,
        or -1 if it hasn't finished yet."""
        if self.sts < 0 and not self.childWaiter:
            self._startChildWaiter()
            self.childWaiter.join( .1 )
        return self.sts

    def wait(self):
        """Wait for and return the exit status of the child process."""
        self.sts = self.process.waitFor()
        # some processes won't terminate until tochild stream is
        # closed, but that's really the responsibility of the caller
        return self.sts

def _makeReaderThread( stream, outfunc, bufsize, name=None, postFunc=None ):
    """Create a thread that reads the stream, calling outfunc for each block,
    and calling postFunc when the end of stream is reached.
    """
    Popen3.count += 1
    name = name or str( Popen3.count )
    threadName = "StreamReader %s" % name
    import threading
    reader = threading.Thread(
        target=_readStream,
        name=threadName,
        args=( stream, outfunc, bufsize, postFunc )
        )

    reader.setDaemon( 1 )
    reader.start()
    return reader
    
def _readStream( instream, outfunc, bufsize, postFunc=None ):
    """Read instream, calling outfunc( buf, 0, count ) with each block.
    Copy streams by passing destStream.write as the outfunc.
    postFunc is called when the end of instream is reached.
    """
    bufsize = bufsize < 1 and 4096 or bufsize
    buf = jarray.zeros( bufsize, 'b' )
    total = 0
    while 1:
        count = instream.read( buf )
        if -1 == count:
            instream.close()
            if postFunc: postFunc()
            break
        else:
            total += count
            outfunc( buf, 0, count )
    return total

class Popen4(Popen3):
    """Popen object that joins the stdout and stderr streams into a single  
    output stream."""
    childerr = None

    def __init__(self, cmd, bufsize=-1):
        Popen3.__init__( self, cmd, 1, bufsize )
        self.closed = Vector() # use a vector for synchronization close()
        self.fromchild = self._join(
            self._fromchild,
            self._childerr,
            bufsize
            )

    def _join( self, stdout, stderr, bufsize ):
        """create a stream that joins two output streams"""
        self._pipeOut = PipedOutputStream()
        joinedStream = PipedInputStream( self._pipeOut )
        self._outReader = _makeReaderThread(
            stdout,
            self._pipeOut.write,
            bufsize,
            "%s-stdout" % self.process,
            self._close
            )
        self._errReader = _makeReaderThread(
            stderr,
            self._pipeOut.write,
            bufsize,
            "%s-stderr" % self.process,
            self._close
            )
        return PyFile( joinedStream )

    def _close( self ):
        """Must be closed twice (once for each of the two joined pipes)"""
        self.closed.add( None )
        if self.closed.size() > 1:
            self._pipeOut.close()

def popen(path, mode='r', bufsize=-1):
    p = Popen3( path, 0, bufsize )
    if mode == 'r':
        return _ProcessFile(p.fromchild, p.process, path)
    elif mode == 'w':
        return _ProcessFile(p.tochild, p.process, path)
    else:
        raise OSError(0, "Invalid mode", mode)

def popen2(path, mode="t", bufsize=-1):
    p = Popen3(path, 0, bufsize)
    return p.fromchild, p.tochild

def popen3(path, mode="t", bufsize=-1):
    p = Popen3(path, 1, bufsize)
    return p.fromchild, p.tochild, p.childerr

def popen4(path, mode="t", bufsize=-1):
    p = Popen4(path, bufsize)
    return p.fromchild, p.tochild

def system( cmd ):
    """Imitate the standard library 'system' call.
    Execute 'cmd' in a shell, and send output to stdout & stderr.

    This is in popen2 only because its Jython implementation is similar to
    that of the popen functions.
    """
    bufsize = 4096
    # this uses some Popen3 internals, and thus belongs in popen3
    # javaos.system should also be this function
    p = Popen3( cmd, 1, bufsize)
    p.tochild.close()
    
    # read stderr in separate thread
    errReader = _makeReaderThread(
        p._childerr,
        System.err.write,
        bufsize,
        "stderr"
        )

    # read stdin in main thread
    _readStream(
        p._fromchild,
        System.out.write,
        bufsize
        )

    status = p.wait()
    return status

def _test():
    # _test comes from python22/lib/popen2.py
    cmd  = "cat"
    teststr = "ab cd\n"
    import os
    if os.name in [ "nt", "java" ]:
        cmd = "more"
    # "more" doesn't act the same way across Windows flavors,
    # sometimes adding an extra newline at the start or the
    # end.  So we strip whitespace off both ends for comparison.
    expected = teststr.strip()
    print "testing popen2..."
    r, w = popen2(cmd)
    w.write(teststr)
    w.close()
    got = r.read()
    if got.strip() != expected:
        raise ValueError("wrote %s read %s" % (teststr, got))
    print "testing popen3..."
    try:
        r, w, e = popen3([cmd])
    except:
        r, w, e = popen3(cmd)
    w.write(teststr)
    w.close()
    got = r.read()
    err = e.read()
    if got.strip() != expected:
        raise ValueError("wrote %s read %s, error %s" % (teststr, got, err ))
    if err:
        raise ValueError("unexected %s on stderr" % err )
# this portion of the test is inapplicable to the Jython implementation
#    for inst in _active[:]:
#        inst.wait()
#    if _active:
#        raise ValueError("_active not empty")
    print "All OK"

    p = Popen3( cmd )
    q = "This is\na test of\nwriting\n"
    p.tochild.write( q )
    p.tochild.close()
    r = p.fromchild.read()
    x = p.poll()
    assert x == 0
    assert r.strip() == q.strip()

if __name__ == '__main__':
    _test()
