import sys

if condition1:
    class MyClass:
        if condition2:
            if sys.version_info < (4,):
                def foo(self):
                    class MyNestedClass:
                        def f0(self):
                            pass
                        if sys.version_info >= (3,):
                            def f1(self):
                                pass
                        elif sys.version_info < (2, 6):
                            def f2(self):
                                pass
                        else:
                            def f3(self):
                                pass

                    MyNestedClass().<caret>