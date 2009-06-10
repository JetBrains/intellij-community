"""
AMAK: 20070515: New select implementation that uses java.nio
"""

import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
from java.nio.channels.SelectionKey import OP_ACCEPT, OP_CONNECT, OP_WRITE, OP_READ

import socket

import errno

class error(Exception): pass

ALL = None

_exception_map = {

# (<javaexception>, <circumstance>) : lambda: <code that raises the python equivalent>

(java.nio.channels.IllegalBlockingModeException, ALL) : error(errno.ESOCKISBLOCKING, 'socket must be in non-blocking mode'),
}

def _map_exception(exc, circumstance=ALL):
    try:
        mapped_exception = _exception_map[(exc.__class__, circumstance)]
        mapped_exception.java_exception = exc
        return mapped_exception
    except KeyError:
        return error(-1, 'Unmapped java exception: <%s:%s>' % (exc.toString(), circumstance))

POLLIN   = 1
POLLOUT  = 2

# The following event types are completely ignored on jython
# Java does not support them, AFAICT
# They are declared only to support code compatibility with cpython

POLLPRI  = 4
POLLERR  = 8
POLLHUP  = 16
POLLNVAL = 32

def _getselectable(selectable_object):
    for method in ['getchannel', 'fileno']:
        try:
            channel = getattr(selectable_object, method)()
            if channel and not isinstance(channel, java.nio.channels.SelectableChannel):
                raise TypeError("Object '%s' is not watchable" % selectable_object, errno.ENOTSOCK)
            return channel
        except:
            pass
    raise TypeError("Object '%s' is not watchable" % selectable_object, errno.ENOTSOCK)

class poll:

    def __init__(self):
        self.selector = java.nio.channels.Selector.open()
        self.chanmap = {}
        self.unconnected_sockets = []

    def _register_channel(self, socket_object, channel, mask):
        jmask = 0
        if mask & POLLIN:
            # Note that OP_READ is NOT a valid event on server socket channels.
            if channel.validOps() & OP_ACCEPT:
                jmask = OP_ACCEPT
            else:
                jmask = OP_READ
        if mask & POLLOUT:
            jmask |= OP_WRITE
            if channel.validOps() & OP_CONNECT:
                jmask |= OP_CONNECT
        selectionkey = channel.register(self.selector, jmask)
        self.chanmap[channel] = (socket_object, selectionkey)

    def _check_unconnected_sockets(self):
        temp_list = []
        for socket_object, mask in self.unconnected_sockets:
            channel = _getselectable(socket_object)
            if channel is not None:
                self._register_channel(socket_object, channel, mask)
            else:
                temp_list.append( (socket_object, mask) )
        self.unconnected_sockets = temp_list

    def register(self, socket_object, mask = POLLIN|POLLOUT|POLLPRI):
        try:
            channel = _getselectable(socket_object)
            if channel is None:
                # The socket is not yet connected, and thus has no channel
                # Add it to a pending list, and return
                self.unconnected_sockets.append( (socket_object, mask) )
                return
            self._register_channel(socket_object, channel, mask)
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def unregister(self, socket_object):
        try:
            channel = _getselectable(socket_object)
            self.chanmap[channel][1].cancel()
            del self.chanmap[channel]
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def _dopoll(self, timeout):
        if timeout is None or timeout < 0:
            self.selector.select()
        else:
            try:
                timeout = int(timeout)
                if timeout == 0:
                    self.selector.selectNow()
                else:
                    # No multiplication required: both cpython and java use millisecond timeouts
                    self.selector.select(timeout)
            except ValueError, vx:
                raise error("poll timeout must be a number of milliseconds or None", errno.EINVAL)
        # The returned selectedKeys cannot be used from multiple threads!
        return self.selector.selectedKeys()

    def poll(self, timeout=None):
        try:
            self._check_unconnected_sockets()
            selectedkeys = self._dopoll(timeout)
            results = []
            for k in selectedkeys.iterator():
                jmask = k.readyOps()
                pymask = 0
                if jmask & OP_READ: pymask |= POLLIN
                if jmask & OP_WRITE: pymask |= POLLOUT
                if jmask & OP_ACCEPT: pymask |= POLLIN
                if jmask & OP_CONNECT: pymask |= POLLOUT
                # Now return the original userobject, and the return event mask
                results.append( (self.chanmap[k.channel()][0], pymask) )
            return results
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

    def close(self):
        try:
            for k in self.selector.keys():
                k.cancel()
            self.selector.close()
        except java.lang.Exception, jlx:
            raise _map_exception(jlx)

def _calcselecttimeoutvalue(value):
    if value is None:
        return None
    try:
        floatvalue = float(value)
    except Exception, x:
        raise TypeError("Select timeout value must be a number or None")
    if value < 0:
        raise error("Select timeout value cannot be negative", errno.EINVAL)
    if floatvalue < 0.000001:
        return 0
    return int(floatvalue * 1000) # Convert to milliseconds

def native_select(read_fd_list, write_fd_list, outofband_fd_list, timeout=None):
    timeout = _calcselecttimeoutvalue(timeout)
    # First create a poll object to do the actual watching.
    pobj = poll()
    try:
        registered_for_read = {}
        # Check the read list
        for fd in read_fd_list:
            pobj.register(fd, POLLIN)
            registered_for_read[fd] = 1
        # And now the write list
        for fd in write_fd_list:
            if registered_for_read.has_key(fd):
            	# registering a second time overwrites the first
                pobj.register(fd, POLLIN|POLLOUT)
            else:
                pobj.register(fd, POLLOUT)
        results = pobj.poll(timeout)
        # Now start preparing the results
        read_ready_list, write_ready_list, oob_ready_list = [], [], []
        for fd, mask in results:
            if mask & POLLIN:
                read_ready_list.append(fd)
            if mask & POLLOUT:
                write_ready_list.append(fd)
        return read_ready_list, write_ready_list, oob_ready_list
    finally:
        # Need to close the poll object no matter what happened
        # If it is left open, it may still have references to sockets
        # That were registered before any exceptions occurred
        pobj.close()

select = native_select

def cpython_compatible_select(read_fd_list, write_fd_list, outofband_fd_list, timeout=None):
    # First turn all sockets to non-blocking
    # keeping track of which ones have changed
    modified_channels = []
    try:
        for socket_list in [read_fd_list, write_fd_list, outofband_fd_list]:
            for s in socket_list:
                channel = _getselectable(s)
                if channel.isBlocking():
                    modified_channels.append(channel)
                    channel.configureBlocking(0)
        return native_select(read_fd_list, write_fd_list, outofband_fd_list, timeout)
    finally:
        for channel in modified_channels:
            channel.configureBlocking(1)
