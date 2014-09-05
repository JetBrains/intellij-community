import os

import pydev_log
from pluginbase import PluginBase



def load_plugins(package):
    plugin_base = PluginBase(package=package)
    plugin_source = plugin_base.make_plugin_source(searchpath=[os.path.dirname(os.path.realpath(__file__)) + '/' + package], persist=True)
    plugins = []
    for plugin in plugin_source.list_plugins():
        loaded_plugin = None
        try:
            loaded_plugin = plugin_source.load_plugin(plugin)
        except:
            pydev_log.error("Failed to load plugin %s" % plugin)
        if loaded_plugin:
            plugins.append(loaded_plugin)

    return plugins


class NullProxy(object):
    def __init__(self):
        def foo(*args, **kwargs):
            return None
        self.null_func = foo

    def __getattr__(self, name):
        return self.null_func

class PluginProxy(object):
    def __init__(self, plugin):
        self.plugin = plugin
        self.cache = {}

    def __getattr__(self, name):
        if not hasattr(self.cache, name):
            self.cache[name] = getattr(self.plugin, name)

        return self.cache[name]




