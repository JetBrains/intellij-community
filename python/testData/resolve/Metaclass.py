class PluginMetaclass(type):
    def getStore(cls):
        pass

class Plugin(object):
    __metaclass__ = PluginMetaclass

    def foo(self):
        Plugin.getStore()
#                    <ref>