import sys
import lib


class FakeModule(object):
    def __getattribute__(self, item):
        raise AttributeError


sys.modules['extra'] = FakeModule()

del FakeModule
del lib
del sys
