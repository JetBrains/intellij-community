# -*- coding: utf-8 -*-

from __future__ import absolute_import, division

import errno
import os
import socket
import struct
import sys

from . import TTransportException


class TSocket(object):
    """Socket implementation for client side."""

    def __init__(self, host=None, port=None, unix_socket=None,
                 sock=None, socket_family=socket.AF_INET,
                 socket_timeout=3000, connect_timeout=None):
        """Initialize a TSocket

        TSocket can be initialized in 3 ways:
        * host + port. can configure to use AF_INET/AF_INET6
        * unix_socket
        * socket. should pass already opened socket here.

        @param host(str)    The host to connect to.
        @param port(int)    The (TCP) port to connect to.
        @param unix_socket(str) The filename of a unix socket to connect to.
        @param sock(socket)     Initialize with opened socket directly.
            If this param used, the host, port and unix_socket params will
            be ignored.
        @param socket_family(str) socket.AF_INET or socket.AF_INET6. only
            take effect when using host/port
        @param socket_timeout   socket timeout in ms
        @param connect_timeout  connect timeout in ms, only used in
            connection, will be set to socket_timeout if not set.
        """
        if sock:
            self.sock = sock
        elif unix_socket:
            self.unix_socket = unix_socket
            self.host = None
            self.port = None
            self.sock = None
        else:
            self.unix_socket = None
            self.host = host
            self.port = port
            self.sock = None

        self.socket_family = socket_family
        self.socket_timeout = socket_timeout / 1000 if socket_timeout else None
        self.connect_timeout = connect_timeout / 1000 if connect_timeout \
            else self.socket_timeout

    def _init_sock(self):
        if self.unix_socket:
            _sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        else:
            _sock = socket.socket(self.socket_family, socket.SOCK_STREAM)
            _sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)

        # socket options
        linger = struct.pack('ii', 0, 0)
        _sock.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, linger)
        _sock.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)

        self.sock = _sock

    def set_handle(self, sock):
        self.sock = sock

    def set_timeout(self, ms):
        """Backward compat api, will bind the timeout to both connect_timeout
        and socket_timeout.
        """
        self.socket_timeout = ms / 1000 if (ms and ms > 0) else None
        self.connect_timeout = self.socket_timeout

        if self.sock is not None:
            self.sock.settimeout(self.socket_timeout)

    def is_open(self):
        return bool(self.sock)

    def open(self):
        self._init_sock()

        addr = self.unix_socket or (self.host, self.port)

        try:
            if self.connect_timeout:
                self.sock.settimeout(self.connect_timeout)

            self.sock.connect(addr)

            if self.socket_timeout:
                self.sock.settimeout(self.socket_timeout)

        except (socket.error, OSError):
            raise TTransportException(
                type=TTransportException.NOT_OPEN,
                message="Could not connect to %s" % str(addr))

    def read(self, sz):
        try:
            buff = self.sock.recv(sz)
        except socket.error as e:
            if (e.args[0] == errno.ECONNRESET and
                    (sys.platform == 'darwin' or
                     sys.platform.startswith('freebsd'))):
                # freebsd and Mach don't follow POSIX semantic of recv
                # and fail with ECONNRESET if peer performed shutdown.
                # See corresponding comment and code in TSocket::read()
                # in lib/cpp/src/transport/TSocket.cpp.
                self.close()
                # Trigger the check to raise the END_OF_FILE exception below.
                buff = ''
            else:
                raise

        if len(buff) == 0:
            raise TTransportException(type=TTransportException.END_OF_FILE,
                                      message='TSocket read 0 bytes')
        return buff

    def write(self, buff):
        self.sock.sendall(buff)

    def flush(self):
        pass

    def close(self):
        if not self.sock:
            return

        try:
            self.sock.shutdown(socket.SHUT_RDWR)
            self.sock.close()
        except (socket.error, OSError):
            pass


class TServerSocket(object):
    """Socket implementation for server side."""

    def __init__(self, host=None, port=None, unix_socket=None,
                 socket_family=socket.AF_INET, client_timeout=3000,
                 backlog=128):
        """Initialize a TServerSocket

        TSocket can be initialized in 2 ways:
        * host + port. can configure to use AF_INET/AF_INET6
        * unix_socket

        @param host(str)    The host to connect to
        @param port(int)    The (TCP) port to connect to
        @param unix_socket(str) The filename of a unix socket to connect to
        @param socket_family(str) socket.AF_INET or socket.AF_INET6. only
            take effect when using host/port
        @param client_timeout   client socket timeout
        @param backlog          backlog for server socket
        """

        if unix_socket:
            self.unix_socket = unix_socket
            self.host = None
            self.port = None
        else:
            self.unix_socket = None
            self.host = host
            self.port = port

        self.socket_family = socket_family
        self.client_timeout = client_timeout / 1000 if client_timeout else None
        self.backlog = backlog

    def _init_sock(self):
        if self.unix_socket:
            # try remove the sock file it already exists
            _sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            try:
                _sock.connect(self.unix_socket)
            except (socket.error, OSError) as err:
                if err.args[0] == errno.ECONNREFUSED:
                    os.unlink(self.unix_socket)
        else:
            _sock = socket.socket(self.socket_family, socket.SOCK_STREAM)

        _sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        if hasattr(socket, "SO_REUSEPORT"):
            _sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
        _sock.settimeout(None)
        self.sock = _sock

    def listen(self):
        self._init_sock()

        addr = self.unix_socket or (self.host, self.port)
        self.sock.bind(addr)
        self.sock.listen(self.backlog)

    def accept(self):
        client, _ = self.sock.accept()
        client.settimeout(self.client_timeout)
        return TSocket(sock=client)

    def close(self):
        if not self.sock:
            return

        try:
            self.sock.shutdown(socket.SHUT_RDWR)
            self.sock.close()
        except (socket.error, OSError):
            pass
