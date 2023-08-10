WRITE_OUT = 1
WRITE_ERR = 2
MAX_STRING_LENGTH = 1000


class ConsoleOutputHook:
    def __init__(self, dbg, out, is_stderr=False):
        self.dbg = dbg
        self.original_out = out
        self.is_error = is_stderr

    def write(self, str):
        if len(str) > MAX_STRING_LENGTH:
            self._write_long_string(str)
        else:
            self._write_short_string(str)

    def flush(self):
        pass

    def __getattr__(self, item):
        # it's called if the attribute wasn't found
        if hasattr(self.original_out, item):
            return getattr(self.original_out, item)
        raise AttributeError("%s has no attribute %s" % (self.original_out, item))

    def _write_long_string(self, str):
        str_len, chunk_size = len(str), len(str) // MAX_STRING_LENGTH
        for i in range(0, str_len, chunk_size):
            self._write_short_string(str[i:i + chunk_size])

    def _write_short_string(self, str):
        if self.is_error:
            cmd = self.dbg.cmd_factory.make_io_message(str, WRITE_ERR)
        else:
            cmd = self.dbg.cmd_factory.make_io_message(str, WRITE_OUT)

        self.dbg.writer.add_command(cmd)
