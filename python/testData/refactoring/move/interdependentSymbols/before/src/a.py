class C:
    pass


def f():
    return A(), C()


class A:
    def m(self):
        f()