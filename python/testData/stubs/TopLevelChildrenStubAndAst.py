import sys
import os.path

from collections import OrderedDict

type Alias = int

TOP = 1
A_1, A_2 = 2, 3

if sys.version_info >= (3, 12):
    import json
    from decimal import Decimal

    type NewAlias = str

    NEW_ONLY = 4

    class NewClass:
        inner_attr = 0

        def method(self): ...

    def new_function(): ...
else:
    import pickle

    OLD_ONLY = 5

    class OldClass: ...

    def old_function(): ...

try:
    import threading
    GUARDED = 6
except ImportError:
    threading = None


class Outer:
    class Nested: ...

    def outer_method(self): ...


def top_function():
    inner = 7

    class LocalClass: ...

    def local_function(): ...
