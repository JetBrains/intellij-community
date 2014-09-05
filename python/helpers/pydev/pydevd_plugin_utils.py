import os
import types

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
            pydev_log.error("Failed to load plugin %s" % plugin, True)
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


def bind_func_to_method(plugin, func_name, obj, method_name):
    foo = types.MethodType(getattr(plugin, func_name), obj)
    setattr(obj, method_name, foo)
    return foo


def clear_bindings(obj, method_prefix):
    for attr in dir(obj):
        if attr.startswith(method_prefix):
            delattr(obj, attr)




