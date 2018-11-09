from libc.stdlib cimport malloc, free
from libc.string cimport memcpy
from libc.stdint cimport int32_t

from thriftpy.transport.cybase cimport (
    TCyBuffer,
    CyTransportBase,
    DEFAULT_BUFFER,
    STACK_STRING_LEN
)

from .. import TTransportException


cdef extern from "../../protocol/cybin/endian_port.h":
    int32_t be32toh(int32_t n)
    int32_t htobe32(int32_t n)


cdef class TCyFramedTransport(CyTransportBase):
    cdef:
        TCyBuffer rbuf, rframe_buf, wframe_buf

    def __init__(self, trans, int buf_size=DEFAULT_BUFFER):
        self.trans = trans
        self.rbuf = TCyBuffer(buf_size)
        self.rframe_buf = TCyBuffer(buf_size)
        self.wframe_buf = TCyBuffer(buf_size)

    cdef read_trans(self, int sz, char *out):
        cdef int i = self.rbuf.read_trans(self.trans, sz, out)
        if i == -1:
            raise TTransportException(TTransportException.END_OF_FILE,
                                      "End of file reading from transport")
        elif i == -2:
            raise MemoryError("grow buffer fail")

    cdef write_rframe_buffer(self, const char *data, int sz):
        cdef int r = self.rframe_buf.write(sz, data)
        if r == -1:
            raise MemoryError("Write to buffer error")

    cdef c_read(self, int sz, char *out):
        if sz <= 0:
            return 0

        while self.rframe_buf.data_size < sz:
            self.read_frame()

        memcpy(out, self.rframe_buf.buf + self.rframe_buf.cur, sz)
        self.rframe_buf.cur += sz
        self.rframe_buf.data_size -= sz

        return sz

    cdef c_write(self, const char *data, int sz):
        cdef int r = self.wframe_buf.write(sz, data)
        if r == -1:
            raise MemoryError("Write to buffer error")

    cdef read_frame(self):
        cdef:
            char frame_len[4]
            char stack_frame[STACK_STRING_LEN]
            char *dy_frame
            int32_t frame_size

        self.read_trans(4, frame_len)
        frame_size = be32toh((<int32_t*>frame_len)[0])

        if frame_size <= 0:
            raise TTransportException("No frame.", TTransportException.UNKNOWN)

        if frame_size <= STACK_STRING_LEN:
            self.read_trans(frame_size, stack_frame)
            self.write_rframe_buffer(stack_frame, frame_size)
        else:
            dy_frame = <char*>malloc(frame_size)
            try:
                self.read_trans(frame_size, dy_frame)
                self.write_rframe_buffer(dy_frame, frame_size)
            finally:
                free(dy_frame)

    cdef c_flush(self):
        cdef:
            bytes data
            char *size_str

        if self.wframe_buf.data_size > 0:
            data = self.wframe_buf.buf[:self.wframe_buf.data_size]
            size = htobe32(self.wframe_buf.data_size)
            size_str = <char*>(&size)

            self.trans.write(size_str[:4] + data)
            self.trans.flush()
            self.wframe_buf.clean()

    def read(self, int sz):
        return self.get_string(sz)

    def write(self, bytes data):
        cdef int sz = len(data)
        self.c_write(data, sz)

    def flush(self):
        self.c_flush()

    def is_open(self):
        return self.trans.is_open()

    def open(self):
        return self.trans.open()

    def close(self):
        return self.trans.close()

    def clean(self):
        self.rbuf.clean()
        self.rframe_buf.clean()
        self.wframe_buf.clean()


class TCyFramedTransportFactory(object):
    def get_transport(self, trans):
        return TCyFramedTransport(trans)
