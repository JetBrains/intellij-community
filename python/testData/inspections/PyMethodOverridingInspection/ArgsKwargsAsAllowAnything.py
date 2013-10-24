class B1:
    def foo(self, a, b):
        pass


class C1(B1):
    def foo(self, *b):
        pass


class B2:
    def foo(self, **kwargs):
        pass


class C2(B2):
    def foo(self):
        pass


class B3:
    def foo(self, *args):
        pass


class C3(B3):
    def foo(self):
        pass
