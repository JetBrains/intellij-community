import sys

if condition1:
    if sys.version_info >= (3,):
        if sys.version_info < (3, 12):
            def foo(): ...
    else:
        if sys.version_info < (2, 2):
            pass
        elif sys.version_info < (2, 5):
            pass
        else:
            class MyClass:
                if sys.version_info < (3, 12):
                    class MyNestedClass:
                        if sys.version_info >= (3, 11):
                            pass
                        else:
                            pass
                else:
                    pass
