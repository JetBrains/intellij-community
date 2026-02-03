class A:
    def foo(self): ...

    def bar(self):
        def baz():
            foo()

        def foo():
            print('baz')