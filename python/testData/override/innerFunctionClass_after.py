class X(object):
    def foo(self):
        pass

class A():
    def service(self):
        class B(X):
            def foo(self):
                super().foo()

