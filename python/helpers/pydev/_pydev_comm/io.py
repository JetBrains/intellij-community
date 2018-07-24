import threading


class PipeIO(object):
    """Thread-safe pipe with blocking reads and writes
    """
    MAX_BUFFER_SIZE = 4096

    def __init__(self):
        self.lock = threading.RLock()
        self.bytes_consumed = threading.Condition(self.lock)
        self.bytes_produced = threading.Condition(self.lock)

        self.buffer = bytearray()
        self.read_pos = 0

    def _bytes_available(self):
        return self.read_pos < len(self.buffer)

    def _reset_buffer(self):
        self.buffer = bytearray()
        self.read_pos = 0

    def read(self, sz):
        """Reads `sz` bytes at most

        Blocks until some data is available in buffer.

        :param sz: the maximum count of bytes to read
        :return: bytes read
        """
        self.lock.acquire()
        try:
            while not self._bytes_available():
                self.bytes_produced.wait()

            read_until_pos = min(self.read_pos + sz, len(self.buffer))
            result = bytes(self.buffer[self.read_pos:read_until_pos])

            self.read_pos = read_until_pos
            self.bytes_consumed.notifyAll()

            return result
        finally:
            self.lock.release()

    def write(self, buf):
        """Writes `buf` content

        Blocks until all `buf` written.

        :param buf: bytes to write
        :return: None
        """
        self.lock.acquire()
        try:
            buf_pos = 0
            while True:
                if buf_pos == len(buf):
                    break

                if len(self.buffer) == self.MAX_BUFFER_SIZE:
                    while self.read_pos < self.MAX_BUFFER_SIZE:
                        self.bytes_consumed.wait()

                    self._reset_buffer()

                bytes_to_write = min(len(buf) - buf_pos, self.MAX_BUFFER_SIZE - len(self.buffer))
                new_buf_pos = buf_pos + bytes_to_write

                self.buffer.extend(buf[buf_pos:new_buf_pos])
                self.bytes_produced.notifyAll()

                buf_pos = new_buf_pos
        finally:
            self.lock.release()
