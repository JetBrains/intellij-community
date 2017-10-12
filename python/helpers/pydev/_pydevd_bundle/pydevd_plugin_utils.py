import pkgutil
import sys
import types

import pydevd_plugins.extensions
from _pydev_bundle import pydev_log
from _pydevd_bundle import pydevd_trace_api

try:
    from pydevd_plugins import django_debug
except:
    django_debug = None
    pydev_log.debug('Unable to load django_debug plugin')

try:
    from pydevd_plugins import jinja2_debug
except:
    jinja2_debug = None
    pydev_log.debug('Unable to load jinja2_debug plugin')

def load_plugins():
    plugins = []
    if django_debug is not None:
        plugins.append(django_debug)

    if jinja2_debug is not None:
        plugins.append(jinja2_debug)
    return plugins


def bind_func_to_method(func, obj, method_name):
    bound_method = types.MethodType(func, obj)

    setattr(obj, method_name, bound_method)
    return bound_method


class PluginManager(object):

    def __init__(self, main_debugger):
        self.plugins = load_plugins()
        self.active_plugins = []
        self.main_debugger = main_debugger
        self.rebind_methods()

    def add_breakpoint(self, func_name, *args, **kwargs):
        # add breakpoint for plugin and remember which plugin to use in tracing
        for plugin in self.plugins:
            if hasattr(plugin, func_name):
                func = getattr(plugin, func_name)
                result = func(self, *args, **kwargs)
                if result:
                    self.activate(plugin)

                    return result
        return None

    def activate(self, plugin):
        if plugin not in self.active_plugins:
            self.active_plugins.append(plugin)
            self.rebind_methods()

    def rebind_methods(self):
        if len(self.active_plugins) == 0:
            self.bind_functions(pydevd_trace_api, getattr, pydevd_trace_api)
        elif len(self.active_plugins) == 1:
            self.bind_functions(pydevd_trace_api, getattr, self.active_plugins[0])
        else:
            self.bind_functions(pydevd_trace_api, create_dispatch, self.active_plugins)

    def bind_functions(self, interface, function_factory, arg):
        for name in dir(interface):
            func = function_factory(arg, name)
            if type(func) == types.FunctionType:
                bind_func_to_method(func, self, name)


class ExtensionManager(object):
    def __init__(self):
        super(ExtensionManager, self).__init__()
        self.loaded_extensions = None
        self.type_to_instance = {}

    def _load_modules(self):
        self.loaded_extensions = []
        for module_loader, name, ispkg in pkgutil.walk_packages(pydevd_plugins.extensions.__path__,
                                                                pydevd_plugins.extensions.__name__ + '.'):
            mod_name = name.split('.')[-1]
            if not ispkg and mod_name.startswith('pydevd_plugin'):
                try:
                    __import__(name)
                    module = sys.modules[name]
                    self.loaded_extensions.append(module)
                except ImportError:
                    pydev_log.error('Unable to load extension ' + name)

    def _ensure_loaded(self):
        if self.loaded_extensions is None:
            self._load_modules()

    def _iter_attr(self):
        for extension in self.loaded_extensions:
            dunder_all = getattr(extension, '__all__', None)
            for attr_name in dir(extension):
                if not attr_name.startswith('_'):
                    if dunder_all is None or attr_name in dunder_all:
                        yield attr_name, getattr(extension, attr_name)

    def get_extension_classes(self, extension_type):
        self._ensure_loaded()
        if extension_type in self.type_to_instance:
            return self.type_to_instance[extension_type]
        handlers = self.type_to_instance.setdefault(extension_type, [])
        for attr_name, attr in self._iter_attr():
            if isinstance(attr, type) and issubclass(attr, extension_type) and attr is not extension_type:
                try:
                    handlers.append(attr())
                except:
                    pydev_log.error('Unable to load extension class' + attr_name, tb=True)
        return handlers


EXTENSION_MANAGER_INSTANCE = ExtensionManager()

def extensions_of_type(extension_type):
    """

    :param T extension_type:  The type of the extension hook
    :rtype: list[T]
    """
    return EXTENSION_MANAGER_INSTANCE.get_extension_classes(extension_type)


def create_dispatch(obj, name):
    def dispatch(self, *args, **kwargs):
        result = None
        for p in self.active_plugins:
            r = getattr(p, name)(self, *args, **kwargs)
            if not result:
                result = r
        return result
    return dispatch








