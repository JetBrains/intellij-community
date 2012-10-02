class C(object):
    def __enter__(self):
        return self

    def __exit__(self, exc, value, traceback):
        return True


def f():
    with C():
        raise Exception()
    x = 1 #pass
