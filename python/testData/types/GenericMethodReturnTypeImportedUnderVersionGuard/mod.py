import sys

if sys.version_info >= (3,):
    from builtins import list as Container
else:
    from builtins import set as Container


class C:
    def m(self) -> Container[str]:
        ...