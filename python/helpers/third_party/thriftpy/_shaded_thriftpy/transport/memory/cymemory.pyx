from libc.string cimport memcpy
from libc.stdlib cimport malloc, free
from thriftpy.transport.cybase cimport (
    TCyBuffer,
    CyTransportBase,
    DEFAULT_BUFFER,
)


def to_bytes(s):
    try:
        return s.encode("utf-8")
    except Exception:
        return s


cdef class TCyMemoryBuffer(CyTransportBase):
    cdef TCyBuffer buf

    def __init__(self, value=b'', int buf_size=DEFAULT_BUFFER):
        self.trans = None
        self.buf = TCyBuffer(buf_size)

        if value:
            self.setvalue(value)

    cdef c_read(self, int sz, char* out):
        if self.buf.data_size < sz:
            sz = self.buf.data_size

        if sz <= 0:
            out[0] = '\0'
        else:
            memcpy(out, self.buf.buf + self.buf.cur, sz)
            self.buf.cur += sz
            self.buf.data_size -= sz

        return sz

    cdef c_write(self, const char* data, int sz):
        cdef int r = self.buf.write(sz, data)
        if r == -1:
            raise MemoryError("Write to memory error")

    cdef _getvalue(self):
        cdef char *out
        cdef int size = self.buf.data_size

        if size <= 0:
            return b''

        out = <char*>malloc(size)
        try:
            memcpy(out, self.buf.buf + self.buf.cur, size)
            return out[:size]
        finally:
            free(out)

    cdef _setvalue(self, int sz, const char *value):
        self.buf.clean()
        self.buf.write(sz, value)

    def read(self, sz):
        return self.get_string(sz)

    def write(self, data):
        data = to_bytes(data)

        cdef int sz = len(data)
        return self.c_write(data, sz)

    def is_open(self):
        return True

    def open(self):
        pass

    def close(self):
        pass

    def flush(self):
        pass

    def clean(self):
        self.buf.clean()

    def getvalue(self):
        return self._getvalue()

    def setvalue(self, value):
        value = to_bytes(value)
        self._setvalue(len(value), value)
