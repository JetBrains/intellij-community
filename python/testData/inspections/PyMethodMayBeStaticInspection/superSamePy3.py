class B:
    def foo(self, text):
        print(type(self), text)


class C(B):
    def foo(self, text):
        super().foo(text)