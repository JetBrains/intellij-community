import os.path
import sys

_helpers_root = os.path.dirname(os.path.abspath(__file__))

for root in sys.path:
    if root != _helpers_root and root != os.curdir:
        print(root)
