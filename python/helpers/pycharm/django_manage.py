#!/usr/bin/env python
import sys
import os

from pycharm_run_utils import adjust_django_sys_path
from fix_getpass import fixGetpass
from _jb_utils import FileChangesTracker, jb_escape_output

try:
    from runpy import run_module
except ImportError:
    from runpy_compat import run_module

adjust_django_sys_path()
base_path = sys.argv.pop()

manage_file = os.getenv('PYCHARM_DJANGO_MANAGE_MODULE')
if not manage_file:
    manage_file = 'manage'


class _PseudoTTY(object):
    """
    Wraps stdin to return "true" for isatty() to fool
    """

    def __init__(self, underlying):
        self.__underlying = underlying

    def __getattr__(self, name):
        return getattr(self.__underlying, name)

    def isatty(self):
        return True


if __name__ == "__main__":
    fixGetpass()
    command = sys.argv[1]
    if command in ["syncdb", "createsuperuser"]:  # List of commands that need stdin to be cheated
        sys.stdin = _PseudoTTY(sys.stdin)
    file_changes_tracker = FileChangesTracker(os.getcwd())
    run_module(manage_file, None, '__main__', True)
    # Report files affected/created by commands. This info is used on Java side.
    changed_files = file_changes_tracker.get_changed_files()
    if changed_files:
        print(jb_escape_output(",".join(changed_files)))

