# -*- coding: utf-8 -*-

from __future__ import absolute_import

import contextlib
import socket
import warnings

from _shaded_thriftpy._compat import PY3, PY35
if PY3:
    import urllib
else:
    import urllib2 as urllib
    import urlparse
    urllib.parse = urlparse
    urllib.parse.quote = urllib.quote

from _shaded_thriftpy.protocol import TBinaryProtocolFactory
from _shaded_thriftpy.server import TThreadedServer
from _shaded_thriftpy.thrift import TProcessor, TClient
from _shaded_thriftpy.transport import (
    TBufferedTransportFactory,
    TServerSocket,
    TSSLServerSocket,
    TSocket,
    TSSLSocket,
)


def make_client(service, host="localhost", port=9090, unix_socket=None,
                proto_factory=TBinaryProtocolFactory(),
                trans_factory=TBufferedTransportFactory(),
                timeout=3000, cafile=None, ssl_context=None, certfile=None,
                keyfile=None, url="", socket_family=socket.AF_INET):
    if url:
        parsed_url = urllib.parse.urlparse(url)
        host = parsed_url.hostname or host
        port = parsed_url.port or port
    if unix_socket:
        socket = TSocket(unix_socket=unix_socket, socket_timeout=timeout)
        if certfile:
            warnings.warn("SSL only works with host:port, not unix_socket.")
    elif host and port:
        if cafile or ssl_context:
            socket = TSSLSocket(host, port, socket_timeout=timeout,
                                socket_family=socket_family, cafile=cafile,
                                certfile=certfile, keyfile=keyfile,
                                ssl_context=ssl_context)
        else:
            socket = TSocket(host, port, socket_family=socket_family, socket_timeout=timeout)
    else:
        raise ValueError("Either host/port or unix_socket or url must be provided.")

    transport = trans_factory.get_transport(socket)
    protocol = proto_factory.get_protocol(transport)
    transport.open()
    return TClient(service, protocol)


def make_server(service, handler,
                host="localhost", port=9090, unix_socket=None,
                proto_factory=TBinaryProtocolFactory(),
                trans_factory=TBufferedTransportFactory(),
                client_timeout=3000, certfile=None):
    processor = TProcessor(service, handler)

    if unix_socket:
        server_socket = TServerSocket(unix_socket=unix_socket)
        if certfile:
            warnings.warn("SSL only works with host:port, not unix_socket.")
    elif host and port:
        if certfile:
            server_socket = TSSLServerSocket(
                host=host, port=port, client_timeout=client_timeout,
                certfile=certfile)
        else:
            server_socket = TServerSocket(
                host=host, port=port, client_timeout=client_timeout)
    else:
        raise ValueError("Either host/port or unix_socket must be provided.")

    server = TThreadedServer(processor, server_socket,
                             iprot_factory=proto_factory,
                             itrans_factory=trans_factory)
    return server


@contextlib.contextmanager
def client_context(service, host="localhost", port=9090, unix_socket=None,
                   proto_factory=TBinaryProtocolFactory(),
                   trans_factory=TBufferedTransportFactory(),
                   timeout=None, socket_timeout=3000, connect_timeout=3000,
                   cafile=None, ssl_context=None, certfile=None, keyfile=None,
                   url=""):
    if url:
        parsed_url = urllib.parse.urlparse(url)
        host = parsed_url.hostname or host
        port = parsed_url.port or port

    if timeout:
        warnings.warn("`timeout` deprecated, use `socket_timeout` and "
                      "`connect_timeout` instead.")
        socket_timeout = connect_timeout = timeout

    if unix_socket:
        socket = TSocket(unix_socket=unix_socket,
                         connect_timeout=connect_timeout,
                         socket_timeout=socket_timeout)
        if certfile:
            warnings.warn("SSL only works with host:port, not unix_socket.")
    elif host and port:
        if cafile or ssl_context:
            socket = TSSLSocket(host, port,
                                connect_timeout=connect_timeout,
                                socket_timeout=socket_timeout,
                                cafile=cafile,
                                certfile=certfile, keyfile=keyfile,
                                ssl_context=ssl_context)
        else:
            socket = TSocket(host, port,
                             connect_timeout=connect_timeout,
                             socket_timeout=socket_timeout)
    else:
        raise ValueError("Either host/port or unix_socket or url must be provided.")

    try:
        transport = trans_factory.get_transport(socket)
        protocol = proto_factory.get_protocol(transport)
        transport.open()
        yield TClient(service, protocol)

    finally:
        transport.close()


if PY35:
    from _shaded_thriftpy.contrib.aio.rpc import (
        make_server as make_aio_server,
        make_client as make_aio_client
    )
