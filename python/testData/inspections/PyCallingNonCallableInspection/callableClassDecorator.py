class D(object):
    def __init__(self, attribute, value):
        pass

    def __call__(self, cls):
        return cls


@D("value", 42)
class C(object):
    pass


a = C()
print(a.value)