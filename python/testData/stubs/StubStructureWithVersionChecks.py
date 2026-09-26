import sys

if condition1:
    if sys.version_info >= (3,):
        if sys.version_info < (3, 12):
            def foo(): ...
    else:
        if sys.version_info < (2, 2):
            type Url = str
            pass
        elif sys.version_info < (2, 5):
            buz = []
        else:
            class MyClass:
                if sys.version_info < (3, 12):
                    class MyNestedClass:
                        if sys.version_info >= (3, 11):
                            def method(self): ...
                        else:
                            s = "x"
                else:
                    i = 1

if (sys.version_info > (2, 1) and ((sys.version_info <= (2, 2) or sys.version_info > (3, )))):
    qux = ""

if sys.version_info <= (2, 1):
    import mod_aaa
    import mod_bbb as bbb
    from mod import ab
    from mod import aba as abb
    from mod import *