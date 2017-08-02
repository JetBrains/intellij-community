class ToClass(object):
    C = 12

    def __init__(self):
        self.a = 1

    def _get(self):
        return 1

    def _set(self, value):
        pass

    def _delete(self):
        pass

    old_property = property(_get, _set, _delete)

    def foo(self):
        pass


class FromClass(ToClass):

    def __init__(self):
        pass

    def lala(self):
        pass