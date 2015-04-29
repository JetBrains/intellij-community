from socket import SHUT_WR
import sys
import time
import traceback

from thrift import TSerialization
from thrift.protocol import TJSONProtocol, TBinaryProtocol
from profiler.ttypes import ProfilerRequest

from prof_util import ProfDaemonThread

import struct

from pydev_imports import _queue
import pydevd_vm_type


def send_message(sock, message):
    """ Send a serialized message (protobuf Message interface)
        to a socket, prepended by its length packed in 4
        bytes (big endian).
    """
    s = TSerialization.serialize(message, TJSONProtocol.TJSONProtocolFactory())
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
    TSerialization.deserialize(msg, msg_buf, TJSONProtocol.TJSONProtocolFactory())

    return msg


def socket_read_n(sock, n):
    """ Read exactly n bytes from the socket.
        Raise RuntimeError if the connection closed before
        n bytes were read.
    """
    buf = ''
    while n > 0:
        data = sock.recv(n)
        if data == '':
            raise RuntimeError('unexpected connection close')
        buf += data
        n -= len(data)
    return buf


class ProfWriter(ProfDaemonThread):
    """ writer thread writes out the commands in an infinite loop """
    def __init__(self, sock):
        ProfDaemonThread.__init__(self)
        self.sock = sock
        self.setName("profiler.Writer")
        self.messageQueue = _queue.Queue()
        if pydevd_vm_type.GetVmType() == 'python':
            self.timeout = 0
        else:
            self.timeout = 0.1

    def addCommand(self, message):
        """ message is NetCommand """
        if not self.killReceived: #we don't take new data after everybody die
            self.messageQueue.put(message)

    def OnRun(self):
        """ just loop and write responses """

        get_has_timeout = sys.hexversion >= 0x02030000 # 2.3 onwards have it.
        try:
            while True:
                try:
                    try:
                        if get_has_timeout:
                            message = self.messageQueue.get(1, 0.1)
                        else:
                            time.sleep(.01)
                            message = self.messageQueue.get(0)
                    except _queue.Empty:
                        if self.killReceived:
                            try:
                                self.sock.shutdown(SHUT_WR)
                                self.sock.close()
                            except:
                                pass

                            return #break if queue is empty and killReceived
                        else:
                            continue
                except:
                    return

                send_message(self.sock, message)

                time.sleep(self.timeout)
        except Exception:
            traceback.print_exc()


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