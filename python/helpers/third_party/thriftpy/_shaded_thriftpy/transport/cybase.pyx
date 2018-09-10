from libc.stdlib cimport malloc, free
from libc.string cimport memcpy, memmove


cdef class TCyBuffer(object):
    def __cinit__(self, buf_size):
        self.buf = <char*>malloc(buf_size)
        self.buf_size = buf_size
        self.cur = 0
        self.data_size = 0

    def __dealloc__(self):
        if self.buf != NULL:
            free(self.buf)
            self.buf = NULL

    cdef void move_to_start(self):
        memmove(self.buf, self.buf + self.cur, self.data_size)
        self.cur = 0

    cdef void clean(self):
        self.cur = 0
        self.data_size = 0

    cdef int write(self, int sz, const char *value):
        cdef:
            int cap = self.buf_size - self.data_size
            int remain = cap - self.cur

        if sz <= 0:
            return 0

        if remain < sz:
            self.move_to_start()

        # recompute remain spaces
        remain = cap - self.cur

        if remain < sz:
            if self.grow(sz - remain + self.buf_size) != 0:
                return -1

        memcpy(self.buf + self.cur + self.data_size, value, sz)
        self.data_size += sz

        return sz

    cdef read_trans(self, trans, int sz, char *out):
        cdef int cap, new_data_len

        if sz <= 0:
            return 0

        if self.data_size < sz:
            if self.buf_size < sz:
                if self.grow(sz) != 0:
                    return -2  # grow buffer error

            cap = self.buf_size - self.data_size

            new_data = trans.read(cap)
            new_data_len = len(new_data)

            while new_data_len + self.data_size < sz:
                more = trans.read(cap - new_data_len)
                more_len = len(more)
                if more_len <= 0:
                    return -1  # end of file error

                new_data += more
                new_data_len += more_len

            if cap - self.cur < new_data_len:
                self.move_to_start()

            memcpy(self.buf + self.cur + self.data_size, <char*>new_data,
                   new_data_len)
            self.data_size += new_data_len

        memcpy(out, self.buf + self.cur, sz)
        self.cur += sz
        self.data_size -= sz

        return sz

    cdef int grow(self, int min_size):
        if min_size <= self.buf_size:
            return 0

        cdef int multiples = min_size / self.buf_size
        if min_size % self.buf_size != 0:
            multiples += 1

        cdef int new_size = self.buf_size * multiples
        cdef char *new_buf = <char*>malloc(new_size)
        if new_buf == NULL:
            return -1
        memcpy(new_buf + self.cur, self.buf + self.cur, self.data_size)
        free(self.buf)
        self.buf_size = new_size
        self.buf = new_buf
        return 0


cdef class CyTransportBase(object):
    cdef c_read(self, int sz, char* out):
        pass

    cdef c_write(self, char* data, int sz):
        pass

    cdef c_flush(self):
        pass

    def clean(self):
        pass

    @property
    def sock(self):
        if not self.trans:
            return
        return getattr(self.trans, 'sock', None)

    cdef get_string(self, int sz):
        cdef:
            char out[STACK_STRING_LEN]
            char *dy_out

        if sz > STACK_STRING_LEN:
            dy_out = <char*>malloc(sz)
            try:
                size = self.c_read(sz, dy_out)
                return dy_out[:size]
            finally:
                free(dy_out)
        else:
            size = self.c_read(sz, out)
            return out[:size]
