from __future__ import with_statement
import os.path
import sys
from warnings import warn

import java.lang.reflect.Array

__all__ = ['add_history', 'clear_history', 'get_begidx', 'get_completer',
           'get_completer_delims', 'get_current_history_length',
           'get_endidx', 'get_history_item', 'get_history_length',
           'get_line_buffer', 'insert_text', 'parse_and_bind',
           'read_history_file', 'read_init_file', 'redisplay',
           'remove_history_item', 'set_completer', 'set_completer_delims',
           'set_history_length', 'set_pre_input_hook', 'set_startup_hook',
           'write_history_file']

try:    
    _reader = sys._jy_interpreter.reader
except AttributeError:
    raise ImportError("Cannot access JLineConsole")

_history_list = None

# The need for the following warnings should go away once we update
# JLine. Choosing ImportWarning as the closest warning to what is
# going on here, namely this is functionality not yet available on
# Jython.

class NotImplementedWarning(ImportWarning):
    """Not yet implemented by Jython"""

class SecurityWarning(ImportWarning):
    """Security manager prevents access to private field"""


def _setup_history():
    # This is obviously not desirable, but avoids O(n) workarounds to
    # modify the history (ipython uses the function
    # remove_history_item to mutate the history relatively frequently)
    global _history_list
    
    history = _reader.history
    try:
        history_list_field = history.class.getDeclaredField("history")
        history_list_field.setAccessible(True)
        _history_list = history_list_field.get(history)
    except:
        pass

_setup_history()

def parse_and_bind(string):
    if string == "tab: complete":
        try:
            keybindings_field = _reader.class.getDeclaredField("keybindings")
            keybindings_field.setAccessible(True)
            keybindings = keybindings_field.get(_reader)
            COMPLETE = _reader.KEYMAP_NAMES.get('COMPLETE')
            if java.lang.reflect.Array.getShort(keybindings, 9) != COMPLETE:
                java.lang.reflect.Array.setShort(keybindings, 9, COMPLETE)
        except:
            warn("Cannot bind tab key to complete. You need to do this in a .jlinebindings.properties file instead", SecurityWarning, stacklevel=2)
    else:
        warn("Cannot bind key %s. You need to do this in a .jlinebindings.properties file instead" % (string,), NotImplementedWarning, stacklevel=2)

def get_line_buffer():
    return str(_reader.cursorBuffer.buffer)

def insert_text(string):
    _reader.putString(string)
    
def read_init_file(filename=None):
    warn("read_init_file: %s" % (filename,), NotImplementedWarning, "module", 2)

def read_history_file(filename="~/.history"):
    print "Reading history:", filename
    expanded = os.path.expanduser(filename)
    new_history = _reader.getHistory().getClass()()
    # new_history.clear()
    with open(expanded) as f:
        for line in f:
            new_history.addToHistory(line.rstrip())
    _reader.history = new_history
    _setup_history()

def write_history_file(filename="~/.history"):
    expanded = os.path.expanduser(filename)
    with open(expanded, 'w') as f:
        for line in _reader.history.historyList:
            f.write(line)
            f.write("\n")

def clear_history():
    _reader.history.clear()

def add_history(line):
    _reader.addToHistory(line)

def get_history_length():
    return _reader.history.maxSize

def set_history_length(length):
    _reader.history.maxSize = length

def get_current_history_length():
    return len(_reader.history.historyList)

def get_history_item(index):
    return _reader.history.historyList[index]

def remove_history_item(pos):
    if _history_list:
        _history_list.remove(pos)
    else:
        warn("Cannot remove history item at position: %s" % (pos,), SecurityWarning, stacklevel=2)

def redisplay():
    _reader.redrawLine()

def set_startup_hook(function=None):
    sys._jy_interpreter.startupHook = function
    
def set_pre_input_hook(function=None):
    warn("set_pre_input_hook %s" % (function,), NotImplementedWarning, stacklevel=2)

_completer_function = None

def set_completer(function=None):
    """set_completer([function]) -> None
    Set or remove the completer function.
    The function is called as function(text, state),
    for state in 0, 1, 2, ..., until it returns a non-string.
    It should return the next possible completion starting with 'text'."""

    global _completer_function
    _completer_function = function

    def complete_handler(buffer, cursor, candidates):
        start = _get_delimited(buffer, cursor)[0]
        delimited = buffer[start:cursor]
        for state in xrange(100): # TODO arbitrary, what's the number used by gnu readline?
            completion = None
            try:
                completion = function(delimited, state)
            except:
                pass
            if completion:
                candidates.add(completion)
            else:
                break
        return start

    _reader.addCompletor(complete_handler)
    

def get_completer():
    return _completer_function

def _get_delimited(buffer, cursor):
    start = cursor
    for i in xrange(cursor-1, -1, -1):
        if buffer[i] in _completer_delims:
            break
        start = i
    return start, cursor

def get_begidx():
    return _get_delimited(str(_reader.cursorBuffer.buffer), _reader.cursorBuffer.cursor)[0]

def get_endidx():
    return _get_delimited(str(_reader.cursorBuffer.buffer), _reader.cursorBuffer.cursor)[1]

def set_completer_delims(string):
    global _completer_delims, _completer_delims_set
    _completer_delims = string
    _completer_delims_set = set(string)

def get_completer_delims():
    return _completer_delims

set_completer_delims(' \t\n`~!@#$%^&*()-=+[{]}\\|;:\'",<>/?')
