import sys

if condition:
    attr0 = 0
    def f0():
        pass
    class MyClass0:
        pass
    if sys.version_info >= (3,):
        attr1 = 1
        def f1():
            pass
        class MyClass1:
            pass
    elif sys.version_info < (2, 6):
        attr2 = 2
        def f2():
            pass
        class MyClass2:
            pass
    else:
        attr3 = 3
        def f3():
            pass
        class MyClass3:
            pass

<caret>