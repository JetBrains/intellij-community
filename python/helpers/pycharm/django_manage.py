#!/usr/bin/env python
import os
import sys

from _jb_utils import FileChangesTracker, jb_escape_output
from fix_getpass import fixGetpass
from pycharm_run_utils import adjust_django_sys_path

try:
    from runpy import run_module
except ImportError:
    from runpy_compat import run_module

adjust_django_sys_path()
base_path = sys.argv.pop()

manage_file = os.getenv('PYCHARM_DJANGO_MANAGE_MODULE')
track_files_pattern = os.environ.get('PYCHARM_TRACK_FILES_PATTERN', None)
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


    def run_command():
        run_module(manage_file, None, '__main__', True)


    if track_files_pattern:
        print("Tracking file by folder pattern: ", track_files_pattern)
        file_changes_tracker = FileChangesTracker(os.getcwd(), track_files_pattern.split(":"))
        run_command()
        # Report files affected/created by commands. This info is used on Java side.
        changed_files = list(file_changes_tracker.get_changed_files())
        if changed_files:
            print("\n" + jb_escape_output(",".join(changed_files)))
    else:
        print("File tracking disabled")
        run_command()
