WRITE_OUT = 1
WRITE_ERR = 2
# Not above MAX_IO_MSG_SIZE in pydevd_comm: make_io_message cuts a longer message down to that size.
MAX_STRING_LENGTH = 1000


class ConsoleOutputHook:
    def __init__(self, dbg, out, is_stderr=False):
        self.dbg = dbg
        self.original_out = out
        self.is_error = is_stderr

    def write(self, string):
        if len(string) > MAX_STRING_LENGTH:
            self._write_long_string(string)
        else:
            self._write_short_string(string)

    def flush(self):
        pass

    def __getattr__(self, item):
        # it's called if the attribute wasn't found
        if hasattr(self.original_out, item):
            return getattr(self.original_out, item)
        raise AttributeError("%s has no attribute %s" % (self.original_out, item))

    def _write_long_string(self, string):
        for i in range(0, len(string), MAX_STRING_LENGTH):
            self._write_short_string(string[i:i + MAX_STRING_LENGTH])

    def _write_short_string(self, string):
        if self.is_error:
            cmd = self.dbg.cmd_factory.make_io_message(string, WRITE_ERR)
        else:
            cmd = self.dbg.cmd_factory.make_io_message(string, WRITE_OUT)

        self.dbg.writer.add_command(cmd)
