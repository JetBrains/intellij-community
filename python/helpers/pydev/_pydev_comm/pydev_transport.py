import socket
import struct
import threading

from _pydev_comm.pydev_io import PipeIO, readall
from _shaded_thriftpy.thrift import TClient
from _shaded_thriftpy.transport import TTransportBase

REQUEST = 0
RESPONSE = 1


class MultiplexedSocketReader(object):

    def __init__(self, s):
        self._socket = s

        self._request_pipe = PipeIO()
        self._response_pipe = PipeIO()

        self._read_socket_lock = threading.RLock()

    def read_request(self, sz):
        """
        Invoked form server-side of the bidirectional transport.
        """
        return self._request_pipe.read(sz)

    def read_response(self, sz):
        """
        Invoked form client-side of the bidirectional transport.
        """
        return self._response_pipe.read(sz)

    def _read_and_dispatch_next_frame(self):
        with self._read_socket_lock:
            direction, frame = self._read_frame()

        if direction == REQUEST:
            self._request_pipe.write(frame)
        elif direction == RESPONSE:
            self._response_pipe.write(frame)

    def _read_frame(self):
        buff = readall(self._socket.recv, 4)
        sz, = struct.unpack('!i', buff)
        if sz == 0:
            # this is an empty message even without a direction byte
            return None, None
        else:
            buff = readall(self._socket.recv, 1)
            direction, = struct.unpack('!b', buff)
            frame = readall(self._socket.recv, sz - 1)
            return direction, frame

    def start_reading(self):
        t = threading.Thread(target=self._read_forever)
        t.daemon = True
        t.start()

    def _read_forever(self):
        try:
            while True:
                self._read_and_dispatch_next_frame()
        except EOFError:
            # normal Python Console termination
            pass
        finally:
            self._close_pipes()

    def _close_pipes(self):
        self._request_pipe.close()
        self._response_pipe.close()


class SocketWriter(object):

    def __init__(self, sock):
        self._socket = sock
        self._send_lock = threading.RLock()

    def write(self, buf):
        with self._send_lock:
            self._socket.sendall(buf)


class FramedWriter(object):
    MAX_BUFFER_SIZE = 4096

    def __init__(self):
        self._buffer = bytearray()

    def _get_writer(self):
        raise NotImplementedError

    def _get_write_direction(self):
        raise NotImplementedError

    def write(self, buf):
        buf_len = len(buf)
        bytes_written = 0
        while bytes_written < buf_len:
            # buffer_size will be updated on self.flush()
            buffer_size = len(self._buffer)

            bytes_to_write = buf_len - bytes_written

            if buffer_size + bytes_to_write > self.MAX_BUFFER_SIZE:
                write_till_byte = bytes_written + (self.MAX_BUFFER_SIZE - buffer_size)

                self._buffer.extend(buf[bytes_written:write_till_byte])
                self.flush()

                bytes_written = write_till_byte
            else:
                # the whole buffer processed
                self._buffer.extend(buf[bytes_written:])
                bytes_written = buf_len

    def flush(self):
        # reset wbuf before write/flush to preserve state on underlying failure
        out = bytes(self._buffer)
        # prepend the message with the direction byte
        out = struct.pack("b", self._get_write_direction()) + out
        self._buffer = bytearray()

        # N.B.: Doing this string concatenation is WAY cheaper than making
        # two separate calls to the underlying socket object. Socket writes in
        # Python turn out to be REALLY expensive, but it seems to do a pretty
        # good job of managing string buffer operations without excessive
        # copies
        self._get_writer().write(struct.pack("!i", len(out)) + out)

    def close(self):
        self._buffer = bytearray()
        pass


class TBidirectionalClientTransport(FramedWriter, TTransportBase):
    def __init__(self, client_socket, reader, writer):
        super(TBidirectionalClientTransport, self).__init__()

        # the following properties will be initialized in `open()`
        self._client_socket = client_socket
        self._reader = reader
        self._writer = writer

        self._is_closed = False

    def _get_writer(self):
        return self._writer

    def _get_write_direction(self):
        return REQUEST

    def _read(self, sz):
        """
        Reads a response from the multiplexed reader.
        """
        return self._reader.read_response(sz)

    def is_open(self):
        return not self._is_closed

    def close(self):
        self._is_closed = True

        self._client_socket.shutdown(socket.SHUT_RDWR)
        self._client_socket.close()


class TServerTransportBase(object):
    """Base class for Thrift server transports."""

    def listen(self):
        pass

    def accept(self):
        raise NotImplementedError

    def close(self):
        pass


class TReversedServerTransport(TServerTransportBase):

    def __init__(self, read_fn, writer):
        self._read_fn = read_fn
        self._writer = writer

    def accept(self):
        return TReversedServerAcceptedTransport(self._read_fn, self._writer)


class TReversedServerAcceptedTransport(FramedWriter):

    def __init__(self, read_fn, writer):
        """
        :param read_fn: hi-level read function (reads solely requests from the input stream)
        :param writer: low-level writer (it is expected to know only bytes)
        """
        super(TReversedServerAcceptedTransport, self).__init__()

        self._read_fn = read_fn
        self._writer = writer

    def _get_writer(self):
        return self._writer

    def _get_write_direction(self):
        # this side acts as a server and writes responses back to the client
        return RESPONSE

    def read(self, sz):
        return self._read_fn(sz)


class TSyncClient(TClient):

    def __init__(self, service, iprot, oprot=None):
        super(TSyncClient, self).__init__(service, iprot, oprot)
        self._lock = threading.RLock()

    def _req(self, _api, *args, **kwargs):
        with self._lock:
            return super(TSyncClient, self)._req(_api, *args, **kwargs)


def open_transports_as_client(addr):
    client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    client_socket.connect(addr)

    return _create_client_server_transports(client_socket)


def open_transports_as_server(addr):
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    server_socket.bind(addr)
    server_socket.listen(1)

    client_socket, address = server_socket.accept()

    raise _create_client_server_transports(client_socket)


def _create_client_server_transports(sock):
    reader = MultiplexedSocketReader(sock)
    reader.start_reading()

    writer = SocketWriter(sock)

    client_transport = TBidirectionalClientTransport(sock, reader, writer)
    server_transport = TReversedServerTransport(client_transport._reader.read_request, client_transport._writer)

    return client_transport, server_transport
