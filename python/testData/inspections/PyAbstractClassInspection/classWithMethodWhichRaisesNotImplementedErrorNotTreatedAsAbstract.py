class A(object):
    def foo(self):
        raise NotImplementedError

    def bar(self):
        print("bar() is called")
        return


class B(A):
    pass
