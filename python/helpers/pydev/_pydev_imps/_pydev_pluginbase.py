# -*- coding: utf-8 -*-
"""
    pluginbase
    ~~~~~~~~~~

    Pluginbase is a module for Python that provides a system for building
    plugin based applications.

    :copyright: (c) Copyright 2014 by Armin Ronacher.
    :license: BSD, see LICENSE for more details.
"""
import os
import sys

from pydevd_constants import IS_PY24, IS_PY3K, IS_JYTHON

if IS_PY24:
    from _pydev_imps._pydev_uuid_old import uuid4
else:
    from uuid import uuid4

if IS_PY3K:
    import pkgutil
else:
    from _pydev_imps import _pydev_pkgutil_old as pkgutil

import errno
try:
    from hashlib import md5
except ImportError:
    from md5 import md5
import threading

from types import ModuleType
from weakref import ref as weakref


PY2 = sys.version_info[0] == 2
if PY2:
    text_type = unicode
    string_types = (unicode, str)
    from cStringIO import StringIO as NativeBytesIO
else:
    text_type = str
    string_types = (str,)
    from io import BytesIO as NativeBytesIO


_local = threading.local()

_internalspace = ModuleType(__name__ + '._internalspace')
_internalspace.__path__ = []
sys.modules[_internalspace.__name__] = _internalspace


def get_plugin_source(module=None, stacklevel=None):
    """Returns the :class:`PluginSource` for the current module or the given
    module.  The module can be provided by name (in which case an import
    will be attempted) or as a module object.

    If no plugin source can be discovered, the return value from this method
    is `None`.

    This function can be very useful if additional data has been attached
    to the plugin source.  For instance this could allow plugins to get
    access to a back reference to the application that created them.

    :param module: optionally the module to locate the plugin source of.
    :param stacklevel: defines how many levels up the module should search
                       for before it discovers the plugin frame.  The
                       default is 0.  This can be useful for writing wrappers
                       around this function.
    """
    if module is None:
        frm = sys._getframe((stacklevel or 0) + 1)
        name = frm.f_globals['__name__']
        glob = frm.f_globals
    elif isinstance(module, string_types):
        frm = sys._getframe(1)
        name = module
        glob = __import__(module, frm.f_globals,
                          frm.f_locals, ['__dict__']).__dict__
    else:
        name = module.__name__
        glob = module.__dict__
    return _discover_space(name, glob)


def _discover_space(name, globals):
    try:
        return _local.space_stack[-1]
    except (AttributeError, IndexError):
        pass

    if '__pluginbase_state__' in globals:
        return globals['__pluginbase_state__'].source

    mod_name = None
    if globals:
        # in unidecode package they pass [] as globals arg
        mod_name = globals.get('__name__')

    if mod_name is not None and \
       mod_name.startswith(_internalspace.__name__ + '.'):
        end = mod_name.find('.', len(_internalspace.__name__) + 1)
        space = sys.modules.get(mod_name[:end])
        if space is not None:
            return space.__pluginbase_state__.source


def _shutdown_module(mod):
    members = list(mod.__dict__.items())
    for key, value in members:
        if key[:1] != '_':
            setattr(mod, key, None)
    for key, value in members:
        setattr(mod, key, None)


def _to_bytes(s):
    if isinstance(s, text_type):
        return s.encode('utf-8')
    return s


class _IntentionallyEmptyModule(ModuleType):

    def __getattr__(self, name):
        try:
            return ModuleType.__getattr__(self, name)
        except AttributeError:
            if name[:2] == '__':
                raise
            raise RuntimeError(
                'Attempted to import from a plugin base module (%s) without '
                'having a plugin source activated.  To solve this error '
                'you have to move the import into a "with" block of the '
                'associated plugin source.' % self.__name__)


class _PluginSourceModule(ModuleType):

    def __init__(self, source):
        modname = '%s.%s' % (_internalspace.__name__, source.spaceid)
        ModuleType.__init__(self, modname)
        self.__pluginbase_state__ = PluginBaseState(source)

    @property
    def __path__(self):
        try:
            ps = self.__pluginbase_state__.source
        except AttributeError:
            return []
        return ps.searchpath + ps.base.searchpath


def _setup_base_package(module_name):
    try:
        mod = __import__(module_name, None, None, ['__name__'])
    except ImportError:
        mod = None
        if '.' in module_name:
            parent_mod = __import__(module_name.rsplit('.', 1)[0],
                                    None, None, ['__name__'])
        else:
            parent_mod = None

    if mod is None:
        mod = _IntentionallyEmptyModule(module_name)
        if parent_mod is not None:
            setattr(parent_mod, module_name.rsplit('.', 1)[-1], mod)
        sys.modules[module_name] = mod


class PluginBase(object):
    """The plugin base acts as a control object around a dummy Python
    package that acts as a container for plugins.  Usually each
    application creates exactly one base object for all plugins.

    :param package: the name of the package that acts as the plugin base.
                    Usually this module does not exist.  Unless you know
                    what you are doing you should not create this module
                    on the file system.
    :param searchpath: optionally a shared search path for modules that
                       will be used by all plugin sources registered.
    """

    def __init__(self, package, searchpath=None):
        #: the name of the dummy package.
        self.package = package
        if searchpath is None:
            searchpath = []
        #: the default search path shared by all plugins as list.
        self.searchpath = searchpath
        _setup_base_package(package)

    def make_plugin_source(self, *args, **kwargs):
        """Creats a plugin source for this plugin base and returns it.
        All parameters are forwarded to :class:`PluginSource`.
        """
        return PluginSource(self, *args, **kwargs)


class PluginSource(object):
    """The plugin source is what ultimately decides where plugins are
    loaded from.  Plugin bases can have multiple plugin sources which act
    as isolation layer.  While this is not a security system it generally
    is not possible for plugins from different sources to accidentally
    cross talk.

    Once a plugin source has been created it can be used in a ``with``
    statement to change the behavior of the ``import`` statement in the
    block to define which source to load the plugins from::

        plugin_source = plugin_base.make_plugin_source(
            searchpath=['./path/to/plugins', './path/to/more/plugins'])

        with plugin_source:
            from myapplication.plugins import my_plugin

    :param base: the base this plugin source belongs to.
    :param identifier: optionally a stable identifier.  If it's not defined
                       a random identifier is picked.  It's useful to set this
                       to a stable value to have consistent tracebacks
                       between restarts and to support pickle.
    :param searchpath: a list of paths where plugins are looked for.
    :param persist: optionally this can be set to `True` and the plugins
                    will not be cleaned up when the plugin source gets
                    garbage collected.
    """
    # Set these here to false by default so that a completely failing
    # constructor does not fuck up the destructor.
    persist = False
    mod = None

    def __init__(self, base, identifier=None, searchpath=None,
                 persist=False):
        #: indicates if this plugin source persists or not.
        self.persist = persist
        if identifier is None:
            identifier = str(uuid4())
        #: the identifier for this source.
        self.identifier = identifier
        #: A reference to the plugin base that created this source.
        self.base = base
        #: a list of paths where plugins are searched in.
        self.searchpath = searchpath
        #: The internal module name of the plugin source as it appears
        #: in the :mod:`pluginsource._internalspace`.
        div = None
        self.spaceid = '_sp' + md5(
            _to_bytes(self.base.package) + _to_bytes('|') +
            _to_bytes(self.identifier)
        ).hexdigest()
        #: a reference to the module on the internal
        #: :mod:`pluginsource._internalspace`.
        self.mod = _PluginSourceModule(self)

        if hasattr(_internalspace, self.spaceid):
            raise RuntimeError('This plugin source already exists.')
        sys.modules[self.mod.__name__] = self.mod
        setattr(_internalspace, self.spaceid, self.mod)

    def __del__(self):
        if not self.persist:
            self.cleanup()

    def list_plugins(self):
        """Returns a sorted list of all plugins that are available in this
        plugin source.  This can be useful to automatically discover plugins
        that are available and is usually used together with
        :meth:`load_plugin`.
        """
        rv = []
        for _, modname, ispkg in pkgutil.iter_modules(self.mod.__path__):
            rv.append(modname)
        return sorted(rv)

    def load_plugin(self, name):
        """This automatically loads a plugin by the given name from the
        current source and returns the module.  This is a convenient
        alternative to the import statement and saves you from invoking
        ``__import__`` or a similar function yourself.

        :param name: the name of the plugin to load.
        """
        if '.' in name:
            raise ImportError('Plugin names cannot contain dots.')

        #with self:
        #    return __import__(self.base.package + '.' + name,
        #                      globals(), {}, ['__name__'])

        self.__assert_not_cleaned_up()
        _local.__dict__.setdefault('space_stack', []).append(self)
        try:
            res = __import__(self.base.package + '.' + name,
                              globals(), {}, ['__name__'])
            return res
        finally:
            try:
                _local.space_stack.pop()
            except (AttributeError, IndexError):
                pass

    def open_resource(self, plugin, filename):
        """This function locates a resource inside the plugin and returns
        a byte stream to the contents of it.  If the resource cannot be
        loaded an :exc:`IOError` will be raised.  Only plugins that are
        real Python packages can contain resources.  Plain old Python
        modules do not allow this for obvious reasons.

        .. versionadded:: 0.3

        :param plugin: the name of the plugin to open the resource of.
        :param filename: the name of the file within the plugin to open.
        """
        mod = self.load_plugin(plugin)
        fn = getattr(mod, '__file__', None)
        if fn is not None:
            if fn.endswith(('.pyc', '.pyo')):
                fn = fn[:-1]
            if os.path.isfile(fn):
                return open(os.path.join(os.path.dirname(fn), filename), 'rb')
        buf = pkgutil.get_data(self.mod.__name__ + '.' + plugin, filename)
        if buf is None:
            raise IOError(errno.ENOEXITS, 'Could not find resource')
        return NativeBytesIO(buf)

    def cleanup(self):
        """Cleans up all loaded plugins manually.  This is necessary to
        call only if :attr:`persist` is enabled.  Otherwise this happens
        automatically when the source gets garbage collected.
        """
        self.__cleanup()

    def __cleanup(self, _sys=sys, _shutdown_module=_shutdown_module):
        # The default parameters are necessary because this can be fired
        # from the destructor and so late when the interpreter shuts down
        # that these functions and modules might be gone.
        if self.mod is None:
            return
        modname = self.mod.__name__
        self.mod.__pluginbase_state__ = None
        self.mod = None
        try:
            delattr(_internalspace, self.spaceid)
        except AttributeError:
            pass
        prefix = modname + '.'
        _sys.modules.pop(modname)
        for key, value in list(_sys.modules.items()):
            if not key.startswith(prefix):
                continue
            mod = _sys.modules.pop(key, None)
            if mod is None:
                continue
            _shutdown_module(mod)

    def __assert_not_cleaned_up(self):
        if self.mod is None:
            raise RuntimeError('The plugin source was already cleaned up.')

    def __enter__(self):
        self.__assert_not_cleaned_up()
        _local.__dict__.setdefault('space_stack', []).append(self)
        return self

    def __exit__(self, exc_type, exc_value, tb):
        try:
            _local.space_stack.pop()
        except (AttributeError, IndexError):
            pass

    def _rewrite_module_path(self, modname):
        self.__assert_not_cleaned_up()
        if modname == self.base.package:
            return self.mod.__name__
        elif modname.startswith(self.base.package + '.'):
            pieces = modname.split('.')
            return self.mod.__name__ + '.' + '.'.join(
                pieces[self.base.package.count('.') + 1:])


class PluginBaseState(object):
    __slots__ = ('_source',)

    def __init__(self, source):
        if source.persist:
            self._source = lambda: source
        else:
            self._source = weakref(source)

    @property
    def source(self):
        rv = self._source()
        if rv is None:
            raise AttributeError('Plugin source went away')
        return rv
