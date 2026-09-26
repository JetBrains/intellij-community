import sys

if sys.version_info < (3, 8):
    def foo() -> int:
        pass
else:
    def foo() -> str:
        pass
