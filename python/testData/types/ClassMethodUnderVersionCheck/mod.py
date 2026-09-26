import sys

if sys.version_info < (2, ):
    pass
else:
    class Foo:
        if sys.version_info < (3, 2):
            def foo(self) -> int:
                pass
        elif sys.version_info < (3, 5):
            def foo(self) -> float:
                pass
        else:
            def foo(self) -> str:
                pass