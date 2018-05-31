import socket
import struct
import sys
import threading
from io import BytesIO, SEEK_CUR

from thriftpy.thrift import TClient
from thriftpy.transport import TTransportBase, readall

REQUEST = 0
RESPONSE = 1


def _read_anything(read_fn, sz):
    buff = b''
    have = 0
    while have == 0:
        chunk = read_fn(sz - have)

        have += len(chunk)
        buff += chunk

    return buff


class MultiplexedSocketReader(object):

    def __init__(self, s):
        self._socket = s

        self._request_buffer = BytesIO()
        self._response_buffer = BytesIO()

        self._request_buffer_lock = threading.RLock()
        self._response_buffer_lock = threading.RLock()

        self._read_socket_lock = threading.RLock()

    def read_request(self, sz):
        """
        Invoked form server-side of the bidirectional transport.
        """
        return _read_anything(self._read_request_buffer, sz)

    def read_response(self, sz):
        """
        Invoked form client-side of the bidirectional transport.
        """
        return _read_anything(self._read_response_buffer, sz)

    def _read_request_buffer(self, sz):
        return self._request_buffer.read(sz)

    def _read_response_buffer(self, sz):
        return self._response_buffer.read(sz)

    # noinspection PyUnusedLocal
    def _try_fill_buffer(self, sz):
        # todo use `sz` argument
        thread_name = threading.current_thread().name

        with self._read_socket_lock:
            direction, frame = self._read_frame()

        if direction == REQUEST:
            with self._request_buffer_lock:
                self._request_buffer.write(frame)
                self._request_buffer.seek(-len(frame), SEEK_CUR)
        elif direction == RESPONSE:
            with self._response_buffer_lock:
                self._response_buffer.write(frame)
                self._response_buffer.seek(-len(frame), SEEK_CUR)

    def _read_frame(self):
        # todo introduce sz argument

        buff = readall(self._socket.recv, 4)
        sz, = struct.unpack('!i', buff)
        if sz == 0:
            # this is an empty message even without a direction byte
            return None, None
        else:
            buff = readall(self._socket.recv, 1)
            # todo is this actually the equivalent of `buff[0]`?
            direction, = struct.unpack('!b', buff)
            frame = readall(self._socket.recv, sz - 1)
            return direction, frame

    def start_reading(self):
        t = threading.Thread(target=self._reading)
        t.start()

    def _reading(self):
        while True:
            self._try_fill_buffer(1)


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
        self._buffer = BytesIO()

    def _get_writer(self):
        raise NotImplementedError

    def _get_write_direction(self):
        raise NotImplementedError

    def write(self, buf):
        buf_len = len(buf)
        bytes_written = 0
        while bytes_written < buf_len:
            # buffer_size will be updated on self.flush()
            buffer_size = sys.getsizeof(self._buffer)

            bytes_to_write = buf_len - bytes_written

            if buffer_size + bytes_to_write > self.MAX_BUFFER_SIZE:
                write_till_byte = bytes_written + (self.MAX_BUFFER_SIZE - buffer_size)

                self._buffer.write(buf[bytes_written:write_till_byte])
                self.flush()

                bytes_written = write_till_byte
            else:
                # the whole buffer processed
                self._buffer.write(buf[bytes_written:])
                bytes_written = buf_len

    def flush(self):
        # reset wbuf before write/flush to preserve state on underlying failure
        out = self._buffer.getvalue()
        # prepend the message with the direction byte
        out = struct.pack("b", self._get_write_direction()) + out
        self._buffer = BytesIO()

        # N.B.: Doing this string concatenation is WAY cheaper than making
        # two separate calls to the underlying socket object. Socket writes in
        # Python turn out to be REALLY expensive, but it seems to do a pretty
        # good job of managing string buffer operations without excessive
        # copies
        self._get_writer().write(struct.pack("!i", len(out)) + out)

    def close(self):
        self._buffer.close()


class TBidirectionalClientTransport(TTransportBase, FramedWriter):
    def __init__(self, host, port):
        super(TBidirectionalClientTransport, self).__init__()

        self.host = host
        self.port = port

        # the following properties will be initialized in `open()`
        self._client_socket = None
        self._reader = None
        self._writer = None
        self._server_transport = None

    def _get_writer(self):
        return self._writer

    def _get_write_direction(self):
        return REQUEST

    def _read(self, sz):
        """
        Reads a response from the multiplexed reader.
        """
        return self._reader.read_response(sz)

    def get_server_transport(self):
        if not self._server_transport:
            raise Exception

        return self._server_transport

    def is_open(self):
        # todo we may try to monitor reads and writes and put a flag if they fail
        return self._client_socket

    def open(self):
        client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_socket.connect((self.host, self.port))

        self._client_socket = client_socket

        self._reader = MultiplexedSocketReader(self._client_socket)
        self._reader.start_reading()

        self._writer = SocketWriter(self._client_socket)

        self._server_transport = TReversedServerTransport(self._reader.read_request, self._writer)

    def close(self):
        # todo should we do something with buffer of the multiplexed reader
        self._client_socket.shutdown(socket.SHUT_RDWR)
        self._client_socket.close()


# todo use TFramedTransport to wrap transport into size-prefixed messages

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
