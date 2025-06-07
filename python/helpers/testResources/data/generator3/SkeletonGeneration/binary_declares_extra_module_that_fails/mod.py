import sys
import lib


class FakeModule(object):
    def __getattribute__(self, item):
        raise AttributeError


sys.modules['mod.extra'] = FakeModule()

del FakeModule
del lib
del sys
