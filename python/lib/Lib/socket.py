"""
This is an updated socket module for use on JVMs > 1.4; it is derived from the
old jython socket module.
The primary extra it provides is non-blocking support.

XXX Restrictions:

- Only INET sockets
- No asynchronous behavior
- No socket options
- Can't do a very good gethostbyaddr() right...
AMAK: 20050527: added socket timeouts
AMAK: 20070515: Added non-blocking (asynchronous) support
AMAK: 20070515: Added client-side SSL support
"""

_defaulttimeout = None

import errno
import jarray
import string
import sys
import threading
import time
import types

# Java.io classes 
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
# Java.io exceptions
import java.io.InterruptedIOException
import java.io.IOException

# Java.lang classes
import java.lang.String
# Java.lang exceptions
import java.lang.Exception

# Java.net classes
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
# Java.net exceptions
import java.net.BindException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

# Java.nio classes
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
# Java.nio exceptions
import java.nio.channels.AlreadyConnectedException
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.CancelledKeyException
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.ClosedChannelException
import java.nio.channels.ClosedSelectorException
import java.nio.channels.ConnectionPendingException
import java.nio.channels.IllegalBlockingModeException
import java.nio.channels.IllegalSelectorException
import java.nio.channels.NoConnectionPendingException
import java.nio.channels.NonReadableChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.NotYetBoundException
import java.nio.channels.NotYetConnectedException
import java.nio.channels.UnresolvedAddressException
import java.nio.channels.UnsupportedAddressTypeException

import javax.net.ssl.SSLSocketFactory
import org.python.core.PyFile

class error(Exception): pass
class herror(error): pass
class gaierror(error): pass
class timeout(error): pass

ALL = None

_exception_map = {

# (<javaexception>, <circumstance>) : lambda: <code that raises the python equivalent>

(java.io.IOException, ALL)            : error(errno.ECONNRESET, 'Software caused connection abort'),
(java.io.InterruptedIOException, ALL) : timeout('timed out'),

(java.net.BindException, ALL)            : error(errno.EADDRINUSE, 'Address already in use'),
(java.net.ConnectException, ALL)         : error(errno.ECONNREFUSED, 'Connection refused'),
(java.net.NoRouteToHostException, ALL)   : error(-1, 'Unmapped exception: java.net.NoRouteToHostException'),
(java.net.PortUnreachableException, ALL) : error(-1, 'Unmapped exception: java.net.PortUnreachableException'),
(java.net.ProtocolException, ALL)        : error(-1, 'Unmapped exception: java.net.ProtocolException'),
(java.net.SocketException, ALL)          : error(-1, 'Unmapped exception: java.net.SocketException'),
(java.net.SocketTimeoutException, ALL)   : timeout('timed out'),
(java.net.UnknownHostException, ALL)     : gaierror(errno.EGETADDRINFOFAILED, 'getaddrinfo failed'),

(java.nio.channels.AlreadyConnectedException, ALL)       : error(errno.EISCONN, 'Socket is already connected'),
(java.nio.channels.AsynchronousCloseException, ALL)      : error(-1, 'Unmapped exception: java.nio.AsynchronousCloseException'),
(java.nio.channels.CancelledKeyException, ALL)           : error(-1, 'Unmapped exception: java.nio.CancelledKeyException'),
(java.nio.channels.ClosedByInterruptException, ALL)      : error(-1, 'Unmapped exception: java.nio.ClosedByInterruptException'),
(java.nio.channels.ClosedChannelException, ALL)          : error(errno.EPIPE, 'Socket closed'),
(java.nio.channels.ClosedSelectorException, ALL)         : error(-1, 'Unmapped exception: java.nio.ClosedSelectorException'),
(java.nio.channels.ConnectionPendingException, ALL)      : error(-1, 'Unmapped exception: java.nio.ConnectionPendingException'),
(java.nio.channels.IllegalBlockingModeException, ALL)    : error(-1, 'Unmapped exception: java.nio.IllegalBlockingModeException'),
(java.nio.channels.IllegalSelectorException, ALL)        : error(-1, 'Unmapped exception: java.nio.IllegalSelectorException'),
(java.nio.channels.NoConnectionPendingException, ALL)    : error(-1, 'Unmapped exception: java.nio.NoConnectionPendingException'),
(java.nio.channels.NonReadableChannelException, ALL)     : error(-1, 'Unmapped exception: java.nio.NonReadableChannelException'),
(java.nio.channels.NonWritableChannelException, ALL)     : error(-1, 'Unmapped exception: java.nio.NonWritableChannelException'),
(java.nio.channels.NotYetBoundException, ALL)            : error(-1, 'Unmapped exception: java.nio.NotYetBoundException'),
(java.nio.channels.NotYetConnectedException, ALL)        : error(-1, 'Unmapped exception: java.nio.NotYetConnectedException'),
(java.nio.channels.UnresolvedAddressException, ALL)      : gaierror(errno.EGETADDRINFOFAILED, 'getaddrinfo failed'),
(java.nio.channels.UnsupportedAddressTypeException, ALL) : error(-1, 'Unmapped exception: java.nio.UnsupportedAddressTypeException'),

}

def would_block_error(exc=None):
    return error(errno.EWOULDBLOCK, 'The socket operation could not complete without blocking')

def _map_exception(exc, circumstance=ALL):
#    print "Mapping exception: %s" % exc
    try:
        mapped_exception = _exception_map[(exc.__class__, circumstance)]
        mapped_exception.java_exception = exc
        return mapped_exception
    except KeyError:
        return error(-1, 'Unmapped java exception: <%s:%s>' % (exc.toString(), circumstance))

MODE_BLOCKING    = 'block'
MODE_NONBLOCKING = 'nonblock'
MODE_TIMEOUT     = 'timeout'

_permitted_modes = (MODE_BLOCKING, MODE_NONBLOCKING, MODE_TIMEOUT)

SHUT_RD   = 0
SHUT_WR   = 1
SHUT_RDWR = 2

class _nio_impl:

    timeout = None
    mode = MODE_BLOCKING

    def read(self, buf):
        bytebuf = java.nio.ByteBuffer.wrap(buf)
        count = self.jchannel.read(bytebuf)
        return count

    def write(self, buf):
        bytebuf = java.nio.ByteBuffer.wrap(buf)
        count = self.jchannel.write(bytebuf)
        return count

    def _setreuseaddress(self, flag):
        self.jsocket.setReuseAddress(flag)

    def _getreuseaddress(self, flag):
        return self.jsocket.getReuseAddress()

    def getpeername(self):
        return (self.jsocket.getInetAddress().getHostName(), self.jsocket.getPort() )

    def config(self, mode, timeout):
        self.mode = mode
        if self.mode == MODE_BLOCKING:
            self.jchannel.configureBlocking(1)
        if self.mode == MODE_NONBLOCKING:
            self.jchannel.configureBlocking(0)
        if self.mode == MODE_TIMEOUT:
            self._timeout_millis = int(timeout*1000)
            self.jsocket.setSoTimeout(self._timeout_millis)

    def close1(self):
        self.jsocket.close()

    def close2(self):
        self.jchannel.close()

    def close3(self):
        if not self.jsocket.isClosed():
            self.jsocket.close()

    def close4(self):
        if not self.jsocket.isClosed():
            if hasattr(self.jsocket, 'shutdownInput') and not self.jsocket.isInputShutdown():
                self.jsocket.shutdownInput()
            if hasattr(self.jsocket, 'shutdownOutput') and not self.jsocket.isOutputShutdown():
                self.jsocket.shutdownOutput()
            self.jsocket.close()

    close = close1
#    close = close2
#    close = close3
#    close = close4

    def shutdownInput(self):
        self.jsocket.shutdownInput()

    def shutdownOutput(self):
        self.jsocket.shutdownOutput()

    def getchannel(self):
        return self.jchannel

    fileno = getchannel

class _client_socket_impl(_nio_impl):

    def __init__(self, socket=None):
        if socket:
            self.jchannel = socket.getChannel()
            self.host = socket.getInetAddress().getHostName()
            self.port = socket.getPort()
        else:
            self.jchannel = java.nio.channels.SocketChannel.open()
            self.host = None
            self.port = None
        self.jsocket = self.jchannel.socket()

    def bind(self, host, port):
        self.jsocket.bind(java.net.InetSocketAddress(host, port))

    def connect(self, host, port):
        self.host = host
        self.port = port
        if self.mode == MODE_TIMEOUT:
            self.jsocket.connect(java.net.InetSocketAddress(self.host, self.port), self._timeout_millis)
        else:
            self.jchannel.connect(java.net.InetSocketAddress(self.host, self.port))

    def finish_connect(self):
        return self.jchannel.finishConnect()

class _server_socket_impl(_nio_impl):

    def __init__(self, host, port, backlog, reuse_addr):
        self.jchannel = java.nio.channels.ServerSocketChannel.open()
        self.jsocket = self.jchannel.socket()
        if host:
            bindaddr = java.net.InetSocketAddress(host, port)
        else:
            bindaddr = java.net.InetSocketAddress(port)
        self._setreuseaddress(reuse_addr)
        self.jsocket.bind(bindaddr, backlog)

    def accept(self):
        if self.mode in (MODE_BLOCKING, MODE_NONBLOCKING):
            new_cli_chan = self.jchannel.accept()
            if new_cli_chan != None:
                return _client_socket_impl(new_cli_chan.socket())
            else:
                return None
        else:
            # In timeout mode now
            new_cli_sock = self.jsocket.accept()
            return _client_socket_impl(new_cli_sock)
        
class _datagram_socket_impl(_nio_impl):

    def __init__(self, port=None, address=None, reuse_addr=0):
        self.jchannel = java.nio.channels.DatagramChannel.open()
        self.jsocket = self.jchannel.socket()
        if port:
            if address is not None:
                local_address = java.net.InetSocketAddress(address, port)
            else:
                local_address = java.net.InetSocketAddress(port)
            self.jsocket.bind(local_address)
        self._setreuseaddress(reuse_addr)

    def connect(self, host, port):
        self.jchannel.connect(java.net.InetSocketAddress(host, port))

    def finish_connect(self):
        return self.jchannel.finishConnect()

    def receive(self, packet):
        self.jsocket.receive(packet)

    def send(self, packet):
        self.jsocket.send(packet)

__all__ = [ 'AF_INET', 'SO_REUSEADDR', 'SOCK_DGRAM', 'SOCK_RAW',
        'SOCK_RDM', 'SOCK_SEQPACKET', 'SOCK_STREAM', 'SOL_SOCKET',
        'SocketType', 'SocketTypes', 'error', 'herror', 'gaierror', 'timeout',
        'getfqdn', 'gethostbyaddr', 'gethostbyname', 'gethostname',
        'socket', 'getaddrinfo', 'getdefaulttimeout', 'setdefaulttimeout',
        'has_ipv6', 'htons', 'htonl', 'ntohs', 'ntohl',
        'SHUT_RD', 'SHUT_WR', 'SHUT_RDWR',
        ]

AF_INET = 2

SOCK_DGRAM = 1
SOCK_STREAM = 2
SOCK_RAW = 3 # not supported
SOCK_RDM = 4 # not supported
SOCK_SEQPACKET = 5 # not supported
SOL_SOCKET = 0xFFFF
SO_REUSEADDR = 4

def _gethostbyaddr(name):
    # This is as close as I can get; at least the types are correct...
    addresses = java.net.InetAddress.getAllByName(gethostbyname(name))
    names = []
    addrs = []
    for addr in addresses:
      names.append(addr.getHostName())
      addrs.append(addr.getHostAddress())
    return (names, addrs)

def getfqdn(name=None):
    """
    Return a fully qualified domain name for name. If name is omitted or empty
    it is interpreted as the local host.  To find the fully qualified name,
    the hostname returned by gethostbyaddr() is checked, then aliases for the
    host, if available. The first name which includes a period is selected.
    In case no fully qualified domain name is available, the hostname is retur
    New in version 2.0.
    """
    if not name:
        name = gethostname()
    names, addrs = _gethostbyaddr(name)
    for a in names:
        if a.find(".") >= 0:
            return a
    return name

def gethostname():
    try:
        return java.net.InetAddress.getLocalHost().getHostName()
    except java.lang.Exception, jlx:
        raise _map_exception(jlx)

def gethostbyname(name):
    try:
        return java.net.InetAddress.getByName(name).getHostAddress()
    except java.lang.Exception, jlx:
        raise _map_exception(jlx)

def gethostbyaddr(name):
    names, addrs = _gethostbyaddr(name)
    return (names[0], names, addrs)

def getservbyname(servicename, protocolname=None):
    # http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4071389
    # How complex is the structure of /etc/services?
    raise NotImplementedError("getservbyname not yet supported on jython.")

def getservbyport(port, protocolname=None):
    # Same situation as above
    raise NotImplementedError("getservbyport not yet supported on jython.")

def getprotobyname(protocolname=None):
    # Same situation as above
    raise NotImplementedError("getprotobyname not yet supported on jython.")

def socket(family = AF_INET, type = SOCK_STREAM, flags=0):
    assert family == AF_INET
    assert type in (SOCK_DGRAM, SOCK_STREAM)
    assert flags == 0
    if type == SOCK_STREAM:
        return _tcpsocket()
    else:
        return _udpsocket()

def getaddrinfo(host, port, family=0, socktype=SOCK_STREAM, proto=0, flags=0):
    return ( (AF_INET, socktype, 0, "", (gethostbyname(host), port)), )

has_ipv6 = 1

def getnameinfo(sock_addr, flags):
    raise NotImplementedError("getnameinfo not yet supported on jython.")

def getdefaulttimeout():
    return _defaulttimeout

def _calctimeoutvalue(value):
    if value is None:
        return None
    try:
        floatvalue = float(value)
    except:
        raise TypeError('Socket timeout value must be a number or None')
    if floatvalue < 0.0:
        raise ValueError("Socket timeout value cannot be negative")
    if floatvalue < 0.000001:
        return 0.0
    return floatvalue

def setdefaulttimeout(timeout):
    global _defaulttimeout
    try:
        _defaulttimeout = _calctimeoutvalue(timeout)
    finally:
        _nonblocking_api_mixin.timeout = _defaulttimeout

def htons(x): return x
def htonl(x): return x
def ntohs(x): return x
def ntohl(x): return x

class _nonblocking_api_mixin:

    timeout = _defaulttimeout
    mode = MODE_BLOCKING

    def gettimeout(self):
        return self.timeout

    def settimeout(self, timeout):
        self.timeout = _calctimeoutvalue(timeout)
        if self.timeout is None:
            self.mode = MODE_BLOCKING
        elif self.timeout < 0.000001:
            self.mode = MODE_NONBLOCKING
        else:
            self.mode = MODE_TIMEOUT
        self._config()

    def setblocking(self, flag):
        if flag:
            self.mode = MODE_BLOCKING
            self.timeout = None
        else:
            self.mode = MODE_NONBLOCKING
            self.timeout = 0.0
        self._config()

    def getblocking(self):
        return self.mode == MODE_BLOCKING

    def _config(self):
        assert self.mode in _permitted_modes
        if self.sock_impl: self.sock_impl.config(self.mode, self.timeout)

    def getchannel(self):
        if not self.sock_impl:
            return None
        return self.sock_impl.getchannel()

    fileno = getchannel

    def _get_jsocket(self):
        return self.sock_impl.jsocket

def _unpack_address_tuple(address_tuple):
    error_message = "Address must be a tuple of (hostname, port)"
    if type(address_tuple) is not type( () ) \
            or type(address_tuple[0]) is not type("") \
            or type(address_tuple[1]) is not type(0):
        raise TypeError(error_message)
    return address_tuple[0], address_tuple[1]

class _tcpsocket(_nonblocking_api_mixin):

    sock_impl = None
    istream = None
    ostream = None
    local_addr = None
    server = 0
    file_count = 0
    reuse_addr = 0

    def bind(self, addr):
        assert not self.sock_impl
        assert not self.local_addr
        # Do the address format check
        host, port = _unpack_address_tuple(addr)
        self.local_addr = addr

    def listen(self, backlog=50):
        "This signifies a server socket"
        try:
            assert not self.sock_impl
            self.server = 1
            if self.local_addr:
                host, port = self.local_addr
            else:
                host, port = "", 0
            self.sock_impl = _server_socket_impl(host, port, backlog, self.reuse_addr)
            self._config()
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def accept(self):
        "This signifies a server socket"
        try:
            if not self.sock_impl:
                self.listen()
            assert self.server
            new_sock = self.sock_impl.accept()
            if not new_sock:
                raise would_block_error()
            cliconn = _tcpsocket()
            cliconn.reuse_addr = new_sock.jsocket.getReuseAddress()
            cliconn.sock_impl = new_sock
            cliconn._setup()
            return cliconn, new_sock.getpeername()
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def _get_host_port(self, addr):
        host, port = _unpack_address_tuple(addr)
        if host == "":
            host = java.net.InetAddress.getLocalHost()
        return host, port

    def _do_connect(self, addr):
        try:
            assert not self.sock_impl
            host, port = self._get_host_port(addr)
            self.sock_impl = _client_socket_impl()
            self.sock_impl._setreuseaddress(self.reuse_addr)
            if self.local_addr: # Has the socket been bound to a local address?
                bind_host, bind_port = self.local_addr
                self.sock_impl.bind(bind_host, bind_port)
            self._config() # Configure timeouts, etc, now that the socket exists
            self.sock_impl.connect(host, port)
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def connect(self, addr):
        "This signifies a client socket"
        self._do_connect(addr)
        self._setup()

    def connect_ex(self, addr):
        "This signifies a client socket"
        self._do_connect(addr)
        if self.sock_impl.finish_connect():
            self._setup()
            return 0
        return errno.EINPROGRESS

    def _setup(self):
        if self.mode != MODE_NONBLOCKING:
            self.istream = self.sock_impl.jsocket.getInputStream()
            self.ostream = self.sock_impl.jsocket.getOutputStream()

    def recv(self, n):
        try:
            if not self.sock_impl: raise error(errno.ENOTCONN, 'Socket is not connected')
            if self.sock_impl.jchannel.isConnectionPending():
                self.sock_impl.jchannel.finishConnect()
            data = jarray.zeros(n, 'b')
            m = self.sock_impl.read(data)
            if m == -1:#indicates EOF has been reached, so we just return the empty string
                return ""
            elif m <= 0:
                if self.mode == MODE_NONBLOCKING:
                    raise would_block_error()
                return ""
            if m < n:
                data = data[:m]
            return data.tostring()
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def recvfrom(self, n):
        return self.recv(n), None

    def send(self, s):
        try:
            if not self.sock_impl: raise error(errno.ENOTCONN, 'Socket is not connected')
            if self.sock_impl.jchannel.isConnectionPending():
                self.sock_impl.jchannel.finishConnect()
            numwritten = self.sock_impl.write(s)
            return numwritten
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    sendall = send

    def getsockname(self):
        try:
            if not self.sock_impl:
                host, port = self.local_addr or ("", 0)
                host = java.net.InetAddress.getByName(host).getHostAddress()
            else:
                if self.server:
                    host = self.sock_impl.jsocket.getInetAddress().getHostAddress()
                else:
                    host = self.sock_impl.jsocket.getLocalAddress().getHostAddress()
                port = self.sock_impl.jsocket.getLocalPort()
            return (host, port)
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def getpeername(self):
        try:
            assert self.sock_impl
            assert not self.server
            host = self.sock_impl.jsocket.getInetAddress().getHostAddress()
            port = self.sock_impl.jsocket.getPort()
            return (host, port)
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)
        
    def setsockopt(self, level, optname, value):
        if optname == SO_REUSEADDR:
            self.reuse_addr = value

    def getsockopt(self, level, optname):
        if optname == SO_REUSEADDR:
            return self.reuse_addr

    def makefile(self, mode="r", bufsize=-1):
        file = None
        if self.istream:
            if self.ostream:
                file = org.python.core.PyFile(self.istream, self.ostream,
                                              "<socket>", mode)
            else:
                file = org.python.core.PyFile(self.istream, "<socket>", mode)
        elif self.ostream:
            file = org.python.core.PyFile(self.ostream, "<socket>", mode)
        else:
            raise IOError, "both istream and ostream have been shut down"
        if file:
            return _tcpsocket.FileWrapper(self, file)

    class FileWrapper:
        def __init__(self, socket, file):
            self.socket  = socket
            self.istream = socket.istream
            self.ostream = socket.ostream

            self.file       = file
            self.read       = file.read
            self.readline   = file.readline
            self.readlines  = file.readlines
            self.write      = file.write
            self.writelines = file.writelines
            self.flush      = file.flush
            self.seek       = file.seek
            self.tell       = file.tell
            self.closed     = file.closed

            self.socket.file_count += 1

        def close(self):
            if self.closed:
                # Already closed
                return

            self.socket.file_count -= 1
            # AMAK: 20070715: Cannot close the PyFile, because closing 
            # it causes the InputStream and OutputStream to be closed.
            # This in turn causes the underlying socket to be closed.
            # This was always true for java.net sockets
            # And continues to be true for java.nio sockets
            # http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4717638
#            self.file.close()
            istream = self.istream
            ostream = self.ostream
            self.istream = None
            self.ostream = None
#            self.closed = self.file.closed
            self.closed = 1

            if self.socket.file_count == 0 and self.socket.sock_impl is None:
                # This is the last file Only close the socket and streams 
                # if there are no outstanding files left.
                istream.close()
                ostream.close()

    def shutdown(self, how):
        assert how in (SHUT_RD, SHUT_WR, SHUT_RDWR)
        assert self.sock_impl
        if how in (SHUT_RD, SHUT_RDWR):
            self.sock_impl.shutdownInput()
        if how in (SHUT_WR, SHUT_RDWR):
            self.sock_impl.shutdownOutput()

    def close(self):
        try:
            if not self.sock_impl:
                return
            sock_impl = self.sock_impl
            istream = self.istream
            ostream = self.ostream
            self.sock_impl = None
            self.istream = None
            self.ostream = None
            # Only close the socket and streams if there are no 
            # outstanding files left.
            if self.file_count == 0:
                if istream:
                    istream.close()
                if ostream:
                    ostream.close()
                if sock_impl:
                    sock_impl.close()
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)
        

class _udpsocket(_nonblocking_api_mixin):

    def __init__(self):
        self.sock_impl = None
        self.addr = None
        self.reuse_addr = 0

    def bind(self, addr):
        try:
            assert not self.sock_impl
            host, port = _unpack_address_tuple(addr)
            host_address = java.net.InetAddress.getByName(host)
            self.sock_impl = _datagram_socket_impl(port, host_address, reuse_addr = self.reuse_addr)
            self._config()
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def _do_connect(self, addr):
        try:
            host, port = _unpack_address_tuple(addr)
            assert not self.addr
            self.addr = addr
            if not self.sock_impl:
                self.sock_impl = _datagram_socket_impl()
                self._config()
                self.sock_impl.connect(host, port)
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def connect(self, addr):
        self._do_connect(addr)

    def connect_ex(self, addr):
        self._do_connect(addr)
        if self.sock_impl.finish_connect():
            return 0
        return errno.EINPROGRESS

    def sendto(self, data, p1, p2=None):
        try:
            if not p2:
                flags, addr = 0, p1
            else:
                flags, addr = 0, p2
            n = len(data)
            if not self.sock_impl:
                self.sock_impl = _datagram_socket_impl()
            host, port = addr
            bytes = java.lang.String(data).getBytes('iso-8859-1')
            a = java.net.InetAddress.getByName(host)
            packet = java.net.DatagramPacket(bytes, n, a, port)
            self.sock_impl.send(packet)
            return n
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def send(self, data):
        if not self.addr: raise error(errno.ENOTCONN, "Socket is not connected")
        return self.sendto(data, self.addr)

    def recvfrom(self, n):
        try:
            assert self.sock_impl
            bytes = jarray.zeros(n, 'b')
            packet = java.net.DatagramPacket(bytes, n)
            self.sock_impl.receive(packet)
            host = None
            if packet.getAddress():
                host = packet.getAddress().getHostName()
            port = packet.getPort()
            m = packet.getLength()
            if m < n:
                bytes = bytes[:m]
            return bytes.tostring(), (host, port)
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def recv(self, n):
        try:
            assert self.sock_impl
            bytes = jarray.zeros(n, 'b')
            packet = java.net.DatagramPacket(bytes, n)
            self.sock_impl.receive(packet)
            m = packet.getLength()
            if m < n:
                bytes = bytes[:m]
            return bytes.tostring()
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def getsockname(self):
        try:
            assert self.sock_impl
            host = self.sock_impl.jsocket.getLocalAddress().getHostName()
            port = self.sock_impl.jsocket.getLocalPort()
            return (host, port)
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def getpeername(self):
        try:
            assert self.sock
            host = self.sock_impl.jsocket.getInetAddress().getHostName()
            port = self.sock_impl.jsocket.getPort()
            return (host, port)
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def __del__(self):
        self.close()

    def close(self):
        try:
            if not self.sock_impl:
                return
            sock = self.sock_impl
            self.sock_impl = None
            sock.close()
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def setsockopt(self, level, optname, value):
        try:
            if optname == SO_REUSEADDR:
                self.reuse_addr = value
#                self.sock._setreuseaddress(value)
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def getsockopt(self, level, optname):
        try:
            if optname == SO_REUSEADDR:
                return self.sock_impl._getreuseaddress()
            else:
                return None
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

SocketType = _tcpsocket
SocketTypes = [_tcpsocket, _udpsocket]

# Define the SSL support

class ssl:

    def __init__(self, plain_sock, keyfile=None, certfile=None):
        self.ssl_sock = self.make_ssl_socket(plain_sock)

    def make_ssl_socket(self, plain_socket, auto_close=0):
        java_net_socket = plain_socket._get_jsocket()
        assert isinstance(java_net_socket, java.net.Socket)
        host = java_net_socket.getInetAddress().getHostName()
        port = java_net_socket.getPort()
        factory = javax.net.ssl.SSLSocketFactory.getDefault();
        ssl_socket = factory.createSocket(java_net_socket, host, port, auto_close)
        ssl_socket.setEnabledCipherSuites(ssl_socket.getSupportedCipherSuites())
        ssl_socket.startHandshake()
        return ssl_socket

    def read(self, n=4096):
        # Probably needs some work on efficency
        in_buf = java.io.BufferedInputStream(self.ssl_sock.getInputStream())
        data = jarray.zeros(n, 'b')
        m = in_buf.read(data, 0, n)
        if m <= 0:
            return ""
        if m < n:
            data = data[:m]
        return data.tostring()

    def write(self, s):
        # Probably needs some work on efficency
        out = java.io.BufferedOutputStream(self.ssl_sock.getOutputStream())
        out.write(s)
        out.flush()

    def _get_server_cert(self):
        return self.ssl_sock.getSession().getPeerCertificates()[0]

    def server(self):
        cert = self._get_server_cert()
        return cert.getSubjectDN().toString()

    def issuer(self):
        cert = self._get_server_cert()
        return cert.getIssuerDN().toString()

def test():
    s = socket(AF_INET, SOCK_STREAM)
    s.connect(("", 80))
    s.send("GET / HTTP/1.0\r\n\r\n")
    while 1:
        data = s.recv(2000)
        print data
        if not data:
            break

if __name__ == '__main__':
    test()
