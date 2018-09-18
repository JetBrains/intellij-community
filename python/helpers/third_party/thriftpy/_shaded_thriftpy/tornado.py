# -*- coding: utf-8 -*-

"""
>>> pingpong = thriftpy.load("pingpong.thrift")
>>>
>>> class Dispatcher(object):
>>>     def ping(self):
>>>         return "pong"

>>> server = make_server(pingpong.PingPong, Dispatcher())
>>> server.listen(6000)
>>> client = ioloop.IOLoop.current().run_sync(
    lambda: make_client(pingpong.PingPong, '127.0.0.1', 6000))
>>> ioloop.IOLoop.current().run_sync(client.ping)
'pong'
"""

from __future__ import absolute_import

from contextlib import contextmanager
from tornado import tcpserver, ioloop, iostream, gen
from io import BytesIO
from datetime import timedelta

from .transport import TTransportException, TTransportBase
from .transport.memory import TMemoryBuffer
from .thrift import TApplicationException, TProcessor, TClient

# TODO need TCyTornadoStreamTransport to work with cython binary protocol
from .protocol.binary import TBinaryProtocolFactory

import logging
import socket
import struct
import toro


logger = logging.getLogger(__name__)


class TTornadoStreamTransport(TTransportBase):
    """a framed, buffered transport over a Tornado stream"""
    DEFAULT_CONNECT_TIMEOUT = timedelta(seconds=1)
    DEFAULT_READ_TIMEOUT = timedelta(seconds=1)

    def __init__(self, host, port, stream=None, io_loop=None, ssl_options=None,
                 read_timeout=DEFAULT_READ_TIMEOUT):
        self.host = host
        self.port = port
        self.io_loop = io_loop or ioloop.IOLoop.current()
        self.read_timeout = read_timeout
        self.is_queuing_reads = False
        self.read_queue = []
        self.__wbuf = BytesIO()
        self._read_lock = toro.Lock()
        self.ssl_options = ssl_options

        # servers provide a ready-to-go stream
        self.stream = stream
        if self.stream is not None:
            self._set_close_callback()

    def with_timeout(self, timeout, future):
        return gen.with_timeout(timeout, future, self.io_loop)

    @gen.coroutine
    def open(self, timeout=DEFAULT_CONNECT_TIMEOUT):
        logger.debug('socket connecting')
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM, 0)
        if self.ssl_options is None:
            self.stream = iostream.IOStream(sock)
        else:
            self.stream = iostream.SSLIOStream(sock, ssl_options=self.ssl_options)

        try:
            yield self.with_timeout(timeout, self.stream.connect(
                (self.host, self.port)))
        except (socket.error, OSError, IOError):
            message = 'could not connect to {}:{}'.format(self.host, self.port)
            raise TTransportException(
                type=TTransportException.NOT_OPEN,
                message=message)

        self._set_close_callback()
        raise gen.Return(self)

    def _set_close_callback(self):
        self.stream.set_close_callback(self.close)

    def close(self):
        # don't raise if we intend to close
        self.stream.set_close_callback(None)
        self.stream.close()

    def read(self, _):
        # The generated code for Tornado shouldn't do individual reads -- only
        # frames at a time
        assert False, "you're doing it wrong"

    @contextmanager
    def io_exception_context(self):
        try:
            yield
        except (socket.error, OSError, IOError) as e:
            raise TTransportException(
                type=TTransportException.END_OF_FILE,
                message=str(e))
        except iostream.StreamBufferFullError as e:
            raise TTransportException(
                type=TTransportException.UNKNOWN,
                message=str(e))
        except gen.TimeoutError as e:
            raise TTransportException(
                type=TTransportException.TIMED_OUT,
                message=str(e))

    @gen.coroutine
    def read_frame(self):
        # IOStream processes reads one at a time
        with (yield self._read_lock.acquire()):
            with self.io_exception_context():
                frame_header = yield self._read_bytes(4)
                if len(frame_header) == 0:
                    raise iostream.StreamClosedError(
                        'Read zero bytes from stream')
                frame_length, = struct.unpack('!i', frame_header)
                logger.debug('received frame header, frame length = %d',
                             frame_length)
                frame = yield self._read_bytes(frame_length)
                logger.debug('received frame payload: %r', frame)
                raise gen.Return(frame)

    def _read_bytes(self, n):
        return self.with_timeout(self.read_timeout, self.stream.read_bytes(n))

    def write(self, buf):
        self.__wbuf.write(buf)

    def flush(self):
        frame = self.__wbuf.getvalue()
        # reset wbuf before write/flush to preserve state on underlying failure
        frame_length = struct.pack('!i', len(frame))
        self.__wbuf = BytesIO()
        with self.io_exception_context():
            return self.stream.write(frame_length + frame)


class TTornadoServer(tcpserver.TCPServer):
    def __init__(self, processor, iprot_factory, oprot_factory=None,
                 transport_read_timeout=TTornadoStreamTransport.DEFAULT_READ_TIMEOUT,  # noqa
                 *args, **kwargs):
        super(TTornadoServer, self).__init__(*args, **kwargs)

        self._processor = processor
        self._iprot_factory = iprot_factory
        self._oprot_factory = (oprot_factory if oprot_factory is not None
                               else iprot_factory)
        self.transport_read_timeout = transport_read_timeout

    @gen.coroutine
    def handle_stream(self, stream, address):
        host, port = address
        trans = TTornadoStreamTransport(
            host=host, port=port, stream=stream,
            io_loop=self.io_loop, read_timeout=self.transport_read_timeout)
        try:
            oprot = self._oprot_factory.get_protocol(trans)
            iprot = self._iprot_factory.get_protocol(TMemoryBuffer())

            while not trans.stream.closed():
                # TODO: maybe read multiple frames in advance for concurrency
                try:
                    frame = yield trans.read_frame()
                except TTransportException as e:
                    if e.type == TTransportException.END_OF_FILE:
                        break
                    else:
                        raise

                iprot.trans.setvalue(frame)
                api, seqid, result, call = self._processor.process_in(iprot)
                if isinstance(result, TApplicationException):
                    self._processor.send_exception(oprot, api, result, seqid)
                else:
                    try:
                        result.success = yield gen.maybe_future(call())
                    except Exception as e:
                        # raise if api don't have throws
                        self._processor.handle_exception(e, result)

                    self._processor.send_result(oprot, api, result, seqid)
        except Exception:
            logger.exception('thrift exception in handle_stream')
            trans.close()

        logger.info('client disconnected %s:%d', host, port)


class TTornadoClient(TClient):
    @gen.coroutine
    def _recv(self, api):
        frame = yield self._oprot.trans.read_frame()
        self._iprot.trans.setvalue(frame)
        result = super(TTornadoClient, self)._recv(api)
        raise gen.Return(result)

    def close(self):
        self._oprot.trans.close()


def make_server(
        service, handler, proto_factory=TBinaryProtocolFactory(),
        io_loop=None, ssl_options=None,
        transport_read_timeout=TTornadoStreamTransport.DEFAULT_READ_TIMEOUT):
    processor = TProcessor(service, handler)
    server = TTornadoServer(processor, iprot_factory=proto_factory,
                            transport_read_timeout=transport_read_timeout,
                            io_loop=io_loop, ssl_options=ssl_options)
    return server


@gen.coroutine
def make_client(
        service, host, port, proto_factory=TBinaryProtocolFactory(),
        io_loop=None, ssl_options=None,
        connect_timeout=TTornadoStreamTransport.DEFAULT_CONNECT_TIMEOUT,
        read_timeout=TTornadoStreamTransport.DEFAULT_READ_TIMEOUT):
    transport = TTornadoStreamTransport(host, port, io_loop=io_loop, ssl_options=ssl_options,
                                        read_timeout=read_timeout)
    iprot = proto_factory.get_protocol(TMemoryBuffer())
    oprot = proto_factory.get_protocol(transport)
    yield transport.open(connect_timeout)
    client = TTornadoClient(service, iprot, oprot)
    raise gen.Return(client)
