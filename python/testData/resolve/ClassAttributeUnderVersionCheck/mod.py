import sys

if sys.version_info < (4,):
    class MyClass:
        if sys.version_info >= (3,):
            def foo(self):
                pass
        elif sys.version_info < (2, 5):
            def bar(self):
                pass
        else:
            def buz(self):
                pass