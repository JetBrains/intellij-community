import sys


# =======================================================================================================================
# BaseStdIn
# =======================================================================================================================
class BaseStdIn:
    def __init__(self, original_stdin=sys.stdin, *args, **kwargs):
        try:
            self.encoding = sys.stdin.encoding
        except:
            # Not sure if it's available in all Python versions...
            pass
        self.original_stdin = original_stdin

    def readline(self, *args, **kwargs):
        # sys.stderr.write('Cannot readline out of the console evaluation\n') -- don't show anything
        # This could happen if the user had done input('enter number).<-- upon entering this, that message would appear,
        # which is not something we want.
        return '\n'

    def write(self, *args, **kwargs):
        pass  # not available StdIn (but it can be expected to be in the stream interface)

    def flush(self, *args, **kwargs):
        pass  # not available StdIn (but it can be expected to be in the stream interface)

    def read(self, *args, **kwargs):
        # in the interactive interpreter, a read and a readline are the same.
        return self.readline()

    def close(self, *args, **kwargs):
        pass  # expected in StdIn

    def __iter__(self):
        # BaseStdIn would not be considered as Iterable in Python 3 without explicit `__iter__` implementation
        return self.original_stdin.__iter__()

    def __getattr__(self, item):
        # it's called if the attribute wasn't found
        if hasattr(self.original_stdin, item):
            return getattr(self.original_stdin, item)
        raise AttributeError("%s has no attribute %s" % (self.original_stdin, item))


# =======================================================================================================================
# StdIn
# =======================================================================================================================
class StdIn(BaseStdIn):
    '''
        Object to be added to stdin (to emulate it as non-blocking while the next line arrives)
    '''

    def __init__(self, interpreter, rpc_client, original_stdin=sys.stdin):
        BaseStdIn.__init__(self, original_stdin)
        self.interpreter = interpreter
        self.rpc_client = rpc_client

    def readline(self, *args, **kwargs):
        from pydev_console.pydev_protocol import KeyboardInterruptException

        # Ok, callback into the client to get the new input
        try:
            requested_input = self.rpc_client.requestInput()
            if not requested_input:
                return '\n'  # Yes, a readline must return something (otherwise we can get an EOFError on the input() call).
            return requested_input
        except KeyboardInterrupt:
            raise  # Let KeyboardInterrupt go through -- #PyDev-816: Interrupting infinite loop in the Interactive Console
        except KeyboardInterruptException:
            # this exception is explicitly declared in `requestInput()` method of `PythonConsoleFrontendService` Thrift service
            # it is thrown on the IDE side and transferred by Thrift library as the response to `requestInput()` method
            raise
        except:
            return '\n'

    def close(self, *args, **kwargs):
        pass  # expected in StdIn

#=======================================================================================================================
# DebugConsoleStdIn
#=======================================================================================================================
class DebugConsoleStdIn(BaseStdIn):
    '''
        Object to be added to stdin (to emulate it as non-blocking while the next line arrives)
    '''

    def __init__(self, dbg, original_stdin):
        BaseStdIn.__init__(self, original_stdin)
        self.debugger = dbg

    def __pydev_run_command(self, is_started):
        try:
            cmd = self.debugger.cmd_factory.make_input_requested_message(is_started)
            self.debugger.writer.add_command(cmd)
        except Exception:
            import traceback
            traceback.print_exc()
            return '\n'

    def readline(self, *args, **kwargs):
        # Notify Java side about input and call original function
        self.__pydev_run_command(True)
        result = self.original_stdin.readline(*args, **kwargs)
        self.__pydev_run_command(False)
        return result
