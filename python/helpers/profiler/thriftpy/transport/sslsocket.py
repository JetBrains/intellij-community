# -*- coding: utf-8 -*-

from __future__ import absolute_import

import os
import socket
import ssl
import struct

from ._ssl import (
    create_thriftpy_context,
    RESTRICTED_SERVER_CIPHERS,
    DEFAULT_CIPHERS
)
from .socket import TSocket, TServerSocket


class TSSLSocket(TSocket):
    """SSL socket implementation for client side
    """

    def __init__(self, host, port, socket_family=socket.AF_INET,
                 socket_timeout=3000, connect_timeout=None,
                 ssl_context=None, validate=True,
                 cafile=None, capath=None, certfile=None, keyfile=None,
                 ciphers=DEFAULT_CIPHERS):
        """Initialize a TSSLSocket

        @param validate(bool)       Set to False to disable SSL certificate
            validation and hostname validation. Default enabled.
        @param cafile(str)          Path to a file of concatenated CA
            certificates in PEM format.
        @param capath(str)           path to a directory containing several CA
            certificates in PEM format, following an OpenSSL specific layout.
        @param certfile(str)        The certfile string must be the path to a
            single file in PEM format containing the certificate as well as
            any number of CA certificates needed to establish the
            certificate’s authenticity.
        @param keyfile(str)         The keyfile string, if not present,
            the private key will be taken from certfile as well.
        @param ciphers(list<str>)   The cipher suites to allow
        @param ssl_context(SSLContext)  Customize the SSLContext, can be used
            to persist SSLContext object. Caution it's easy to get wrong, only
            use if you know what you're doing.

        The `host` must be the same with server if validate enabled.
        """
        super(TSSLSocket, self).__init__(
            host=host, port=port, socket_family=socket_family,
            connect_timeout=connect_timeout, socket_timeout=socket_timeout)

        if ssl_context:
            self.ssl_context = ssl_context
        else:
            self.ssl_context = create_thriftpy_context(server_side=False,
                                                       ciphers=ciphers)

            if cafile or capath:
                self.ssl_context.load_verify_locations(cafile=cafile,
                                                       capath=capath)

            if certfile:
                self.ssl_context.load_cert_chain(certfile, keyfile=keyfile)

            if not validate:
                self.ssl_context.check_hostname = False
                self.ssl_context.verify_mode = ssl.CERT_NONE

    def _init_sock(self):
        _sock = socket.socket(self.socket_family, socket.SOCK_STREAM)
        _sock = self.ssl_context.wrap_socket(_sock,
                                             server_hostname=self.host)
        # socket options
        linger = struct.pack('ii', 0, 0)
        _sock.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, linger)
        _sock.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
        _sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.sock = _sock


class TSSLServerSocket(TServerSocket):
    """SSL implementation of TServerSocket
    """

    def __init__(self, host, port, socket_family=socket.AF_INET,
                 client_timeout=3000, backlog=128,
                 ssl_context=None, certfile='cert.pem',
                 ciphers=RESTRICTED_SERVER_CIPHERS):
        """Initialize a TSSLServerSocket

        @param certfile(str)        The server cert pem filename
        @param ciphers(list<str>)   The cipher suites to allow
        @param ssl_context(SSLContext)  Customize the SSLContext, can be used
            to persist SSLContext object. Caution it's easy to get wrong, only
            use if you know what you're doing.
        """
        super(TSSLServerSocket, self).__init__(
            host=host, port=port, socket_family=socket_family,
            client_timeout=client_timeout, backlog=backlog)

        if ssl_context:
            self.ssl_context = ssl_context
        else:
            if not os.access(certfile, os.R_OK):
                raise IOError('No such certfile found: %s' % certfile)

            self.ssl_context = create_thriftpy_context(server_side=True,
                                                       ciphers=ciphers)
            self.ssl_context.load_cert_chain(certfile=certfile)

    def accept(self):
        sock, _ = self.sock.accept()
        try:
            ssl_sock = self.ssl_context.wrap_socket(sock, server_side=True)
        except ssl.SSLError:
            # failed handshake/ssl wrap, close socket to client
            sock.shutdown(socket.SHUT_RDWR)
            sock.close()
            raise
        else:
            ssl_sock.settimeout(self.client_timeout)
            return TSocket(sock=ssl_sock)
