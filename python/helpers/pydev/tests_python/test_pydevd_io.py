from _pydevd_bundle.pydevd_io import IORedirector
from _pydevd_bundle.pydevd_comm import NetCommandFactory


def test_io_redirector():

    class MyRedirection1(object):
        pass

    class MyRedirection2(object):
        pass

    # Check that we don't fail creating the IORedirector if the original
    # doesn't have a 'buffer'.
    IORedirector(MyRedirection1(), MyRedirection2(), wrap_buffer=True)


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

