WRITE_OUT = 1
WRITE_ERR = 2


class ConsoleOutputHook:
    def __init__(self, dbg, out, is_stderr=False):
        self.dbg = dbg
        self.original_out = out
        self.is_error = is_stderr

    def write(self, str):
        if self.is_error:
            cmd = self.dbg.cmd_factory.make_io_message(str, WRITE_ERR)
        else:
            cmd = self.dbg.cmd_factory.make_io_message(str, WRITE_OUT)

        self.dbg.writer.add_command(cmd)

    def flush(self):
        pass

    def __getattr__(self, item):
        # it's called if the attribute wasn't found
        if hasattr(self.original_out, item):
            return getattr(self.original_out, item)
        raise AttributeError("%s has no attribute %s" % (self.original_out, item))
