import sys


class FakeModule(object):
    def __getattribute__(self, item):
        import another
        return object.__getattribute__(self, item)


sys.modules['extra'] = FakeModule()

del FakeModule
del sys
