import struct
import traceback

from _prof_imports import IS_PY3K
from _prof_imports import ProfilerRequest
from _prof_imports import TBinaryProtocolFactory
from _prof_imports import serialize, deserialize
from prof_util import ProfDaemonThread


def send_message(sock, message):
    """ Send a serialized message (protobuf Message interface)
        to a socket, prepended by its length packed in 4
        bytes (big endian).
    """
    s = serialize(message, TBinaryProtocolFactory())
    packed_len = struct.pack('>L', len(s))
    sock.sendall(packed_len + s)


def get_message(sock, msgtype):
    """ Read a message from a socket. msgtype is a subclass of
        of protobuf Message.
    """
    len_buf = socket_read_n(sock, 4)
    msg_len = struct.unpack('>L', len_buf)[0]
    msg_buf = socket_read_n(sock, msg_len)

    msg = msgtype()
    deserialize(msg, msg_buf, TBinaryProtocolFactory())

    return msg


def socket_read_n(sock, n):
    """ Read exactly n bytes from the socket.
        Raise RuntimeError if the connection closed before
        n bytes were read.
    """
    if IS_PY3K:
        buf = bytearray()
    else:
        buf = ''

    while n > 0:
        data = sock.recv(n)
        if data == '':
            raise RuntimeError('unexpected connection close')
        buf += data
        n -= len(data)
    return buf


class ProfWriter(object):
    """ writer thread writes out the commands in an infinite loop """
    def __init__(self, sock):
        self.sock = sock

    def addCommand(self, message):
        send_message(self.sock, message)

class ProfReader(ProfDaemonThread):
    """ reader thread reads and dispatches commands in an infinite loop """

    def __init__(self, sock, message_processor):
        ProfDaemonThread.__init__(self)
        self.sock = sock
        self.processor = message_processor
        self.setName("profiler.Reader")

    def OnRun(self):
        try:
            while not self.killReceived:
                try:
                    message = get_message(self.sock, ProfilerRequest)
                except:
                    traceback.print_exc()
                    return # Finished communication.

                try:
                    self.processor.process(message)
                except:
                    traceback.print_exc()

        except:
            traceback.print_exc()