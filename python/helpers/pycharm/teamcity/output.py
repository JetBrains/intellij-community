# coding=utf-8
import errno
import sys
from functools import wraps
import time

# Capture some time functions to allow monkeypatching them in tests
_time = time.time
_stderr = sys.stderr


class TeamCityMessagesPrinter(object):
    def __init__(self, output=None, context_manager=None):
        if output is None:
            output = sys.stdout
        if sys.version_info < (3,) or not hasattr(output, 'buffer'):
            self.output = output
        else:
            self.output = output.buffer
        # context manager can be set for testing frameworks such as pytest, which may redirect
        # normal output, to temporarily disable redirection for Teamcity messages
        self.context_manager = context_manager

    def send_message(self, message):
        if self.context_manager:
            with self.context_manager():
                self._output(message)
        else:
            self._output(message)

    def _output(self, message):
        # Python may buffer it for a long time, flushing helps to see real-time result
        retry_on_EAGAIN(self.output.write)(message)
        retry_on_EAGAIN(self.output.flush)()


def retry_on_EAGAIN(callable):
    # output seems to be non-blocking when running under teamcity.
    @wraps(callable)
    def wrapped(*args, **kwargs):
        start_time = _time()
        while True:
            try:
                return callable(*args, **kwargs)
            except IOError as e:
                if e.errno != errno.EAGAIN:
                    raise
                # Give up after a minute.
                if _time() - start_time > 60:
                    raise
                time.sleep(.1)

    return wrapped
