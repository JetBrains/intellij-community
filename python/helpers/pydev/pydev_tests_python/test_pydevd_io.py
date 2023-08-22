from _pydevd_bundle.pydevd_io import IORedirector
from _pydevd_bundle.pydevd_comm import NetCommandFactory
import pytest


def test_io_redirector():

    class MyRedirection1(object):
        encoding = 'foo'

    class MyRedirection2(object):
        pass

    my_redirector = IORedirector(MyRedirection1(), MyRedirection2(), wrap_buffer=True)
    none_redirector = IORedirector(None, None, wrap_buffer=True)

    assert my_redirector.encoding == 'foo'
    with pytest.raises(AttributeError):
        none_redirector.encoding

    # Check that we don't fail creating the IORedirector if the original
    # doesn't have a 'buffer'.
    for redirector in (
            my_redirector,
            none_redirector,
        ):
        redirector.write('test')
        redirector.flush()

    assert not redirector.isatty()


class _DummyWriter(object):

    __slots__ = ['commands', 'command_meanings']

    def __init__(self):
        self.commands = []
        self.command_meanings = []

    def add_command(self, cmd):
        from _pydevd_bundle.pydevd_comm import ID_TO_MEANING
        meaning = ID_TO_MEANING[str(cmd.id)]
        self.command_meanings.append(meaning)
        self.commands.append(cmd)


class _DummyPyDb(object):

    def __init__(self):
        self.cmd_factory = NetCommandFactory()
        self.writer = _DummyWriter()


def test_debug_console():
    from _pydev_bundle.pydev_console_utils import DebugConsoleStdIn

    class OriginalStdin(object):

        def readline(self):
            return 'read'

    original_stdin = OriginalStdin()

    py_db = _DummyPyDb()
    debug_console_std_in = DebugConsoleStdIn(py_db, original_stdin)
    assert debug_console_std_in.readline() == 'read'

    assert py_db.writer.command_meanings == ['CMD_INPUT_REQUESTED', 'CMD_INPUT_REQUESTED']

