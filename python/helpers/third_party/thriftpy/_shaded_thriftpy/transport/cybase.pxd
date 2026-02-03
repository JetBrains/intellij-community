cdef enum:
    DEFAULT_BUFFER = 4096
    STACK_STRING_LEN = 4096

cdef class TCyBuffer(object):
    cdef:
        char *buf
        int cur, buf_size, data_size

        void move_to_start(self)
        void clean(self)
        int write(self, int sz, const char *value)
        int grow(self, int min_size)
        read_trans(self, trans, int sz, char *out)


cdef class CyTransportBase(object):
    cdef object trans

    cdef c_read(self, int sz, char* out)
    cdef c_write(self, char* data, int sz)
    cdef c_flush(self)

    cdef get_string(self, int sz)
