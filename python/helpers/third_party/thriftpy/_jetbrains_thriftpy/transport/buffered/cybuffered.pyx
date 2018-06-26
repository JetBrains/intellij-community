from thriftpy.transport.cybase cimport (
    TCyBuffer,
    CyTransportBase,
    DEFAULT_BUFFER
)

from .. import TTransportException

DEF MIN_BUFFER_SIZE = 1024


cdef class TCyBufferedTransport(CyTransportBase):
    """binary reader/writer"""

    cdef:
        TCyBuffer rbuf, wbuf

    def __init__(self, trans, int buf_size=DEFAULT_BUFFER):
        if buf_size < MIN_BUFFER_SIZE:
            raise Exception("buffer too small")

        self.trans = trans
        self.rbuf = TCyBuffer(buf_size)
        self.wbuf = TCyBuffer(buf_size)

    def clean(self):
        self.rbuf.clean()
        self.wbuf.clean()

    def is_open(self):
        return self.trans.is_open()

    def open(self):
        return self.trans.open()

    def close(self):
        return self.trans.close()

    def write(self, bytes data):
        cdef int sz = len(data)
        return self.c_write(data, sz)

    def read(self, int sz):
        return self.get_string(sz)

    def flush(self):
        return self.c_flush()

    cdef c_write(self, const char *data, int sz):
        cdef:
            int cap = self.wbuf.buf_size - self.wbuf.data_size
            int r

        if cap < sz:
            self.c_flush()

        r = self.wbuf.write(sz, data)
        if r == -1:
            raise MemoryError("Write to buffer error")

    cdef c_read(self, int sz, char* out):
        if sz <= 0:
            return 0

        self.read_trans(sz, out)
        return sz

    cdef read_trans(self, int sz, char *out):
        cdef int i = self.rbuf.read_trans(self.trans, sz, out)
        if i == -1:
            raise TTransportException(TTransportException.END_OF_FILE,
                                      "End of file reading from transport")
        elif i == -2:
            raise MemoryError("grow read buffer fail")

    cdef c_flush(self):
        cdef bytes data
        if self.wbuf.data_size > 0:
            data = self.wbuf.buf[:self.wbuf.data_size]
            self.trans.write(data)
            self.trans.flush()
            self.wbuf.clean()

    def getvalue(self):
        return self.trans.getvalue()


class TCyBufferedTransportFactory(object):
    def get_transport(self, trans):
        return TCyBufferedTransport(trans)
