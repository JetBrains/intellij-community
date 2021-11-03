import sys

class sdist_add_defaults:
    if sys.version_info < (3, 7):
        def add_defaults(self) -> None: ...
