import os.path
import sys

_helpers_root = os.path.dirname(os.path.abspath(__file__))
_working_dir = os.getcwd()

for root in sys.path:
    # The current working dir is not automatically included but can be if PYTHONPATH
    # contains an empty entry.
    if root != _helpers_root and root != os.curdir and root != _working_dir:
        print(root)
