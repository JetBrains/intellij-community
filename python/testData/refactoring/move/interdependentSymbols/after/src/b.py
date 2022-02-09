from a import C


def f():
    return A(), C()


class A:
    def m(self):
        f()
