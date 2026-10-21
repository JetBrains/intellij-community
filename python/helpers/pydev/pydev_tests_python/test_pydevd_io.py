import os
import re
from xml.sax.saxutils import unescape

from _pydevd_bundle.pydevd_io import IORedirector
from _pydevd_bundle.pydevd_comm import MAX_IO_MSG_SIZE, NetCommandFactory
from _pydevd_bundle.pydevd_console_output import MAX_STRING_LENGTH, ConsoleOutputHook
import pytest

try:
    from urllib.parse import unquote
except ImportError:
    from urllib import unquote


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


def _console_text_of(command):
    """The text and the ctx the IDE reads out of one CMD_WRITE_TO_CONSOLE command."""
    wire = command._as_bytes
    if not isinstance(wire, str):
        wire = wire.decode('utf-8')
    xml = unquote(wire.split('\t', 2)[2])
    io = re.search(r'<io s="(.*)" ctx="(\d)"/>', xml, re.DOTALL)
    text = unquote(unescape(io.group(1), {'&quot;': '"'}))
    if not isinstance(text, type(u'')):
        text = text.decode('utf-8')
    return text, io.group(2)


def _assert_console_receives_whole(written, is_stderr):
    py_db = _DummyPyDb()
    ConsoleOutputHook(py_db, None, is_stderr=is_stderr).write(written)

    sent = [_console_text_of(command) for command in py_db.writer.commands]
    received = ''.join(text for text, _ in sent)
    assert len(os.path.commonprefix([received, written])) == len(written)
    assert len(received) == len(written)
    assert set(py_db.writer.command_meanings) == set(['CMD_WRITE_TO_CONSOLE'])
    assert set(ctx for _, ctx in sent) == set(['2' if is_stderr else '1'])
    # PY-93295: a 1,500-character write used to travel as 1,500 one-character messages.
    assert len(sent) == (len(written) + MAX_STRING_LENGTH - 1) // MAX_STRING_LENGTH
    assert max(len(text) for text, _ in sent) <= MAX_IO_MSG_SIZE


# PY-93295: past 1,000,000 characters every piece of a write was cut to 1,000 characters and '...'.
@pytest.mark.parametrize('is_stderr', [False, True])
@pytest.mark.parametrize('length', [1000, 1001, 1999, 2000, 1000000, 1001000, 2000003])
def test_console_output_hook_sends_a_write_whole(length, is_stderr):
    written = ''.join('%07d' % i for i in range(length // 7 + 1))[:length]
    _assert_console_receives_whole(written, is_stderr)


def test_console_output_hook_sends_a_non_ascii_write_whole():
    _assert_console_receives_whole(u'\u0438' * 2000003, is_stderr=False)

