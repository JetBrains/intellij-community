# -*- coding: utf-8 -*-
# Copyright (c) 2018 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""A pocket full of useful reflection functions!"""

from __future__ import absolute_import, print_function

import inspect
import functools
from os.path import basename
from pkgutil import iter_modules

import six
from six import string_types

from pockets.collections import listify
from pockets.string import splitify


__all__ = [
    "collect_subclasses",
    "collect_superclasses",
    "collect_superclass_attr_names",
    "hoist_submodules",
    "import_star",
    "import_submodules",
    "is_data",
    "resolve",
    "unwrap",
]


def collect_subclasses(cls):
    """
    Recursively collects all descendant subclasses that inherit from the
    given class, not including the class itself.

    Note:
        Does not include `cls` itself.

    Args:
        cls (class): The class object from which the collection should begin.

    Returns:
        list: A list of `class` objects that inherit from `cls`. This list
            will not include `cls` itself.
    """
    subclasses = set()
    for subclass in cls.__subclasses__():
        subclasses.add(subclass)
        subclasses.update(collect_subclasses(subclass))
    return list(subclasses)


def collect_superclasses(cls, terminal_class=None, modules=None):
    """
    Recursively collects all ancestor superclasses in the inheritance
    hierarchy of the given class, including the class itself.

    Note:
        Inlcudes `cls` itself. Will not include `terminal_class`.

    Args:
        cls (class): The class object from which the collection should begin.
        terminal_class (class or list): If `terminal_class` is encountered in
            the hierarchy, we stop ascending the tree. `terminal_class` will
            not be included in the returned list.
        modules (string, module, or list): If `modules` is passed, we only
            return classes that are in the given module/modules. This can be
            used to exclude base classes that come from external libraries.

    Returns:
        list: A list of `class` objects from which `cls` inherits. This list
            will include `cls` itself.
    """
    terminal_class = listify(terminal_class)
    if modules is not None:
        modules = listify(modules)
        module_strings = []
        for m in modules:
            if isinstance(m, six.string_types):
                module_strings.append(m)
            else:
                module_strings.append(m.__name__)
        modules = module_strings

    superclasses = set()
    is_in_module = modules is None or cls.__module__ in modules
    if is_in_module and cls not in terminal_class:
        superclasses.add(cls)
        for base in cls.__bases__:
            superclasses.update(
                collect_superclasses(base, terminal_class, modules)
            )

    return list(superclasses)


def collect_superclass_attr_names(cls, terminal_class=None, modules=None):
    """
    Recursively collects all attribute names of ancestor superclasses in the
    inheritance hierarchy of the given class, including the class itself.

    Note:
        Inlcudes `cls` itself. Will not include `terminal_class`.

    Args:
        cls (class): The class object from which the collection should begin.
        terminal_class (class or list): If `terminal_class` is encountered in
            the hierarchy, we stop ascending the tree. Attributes from
            `terminal_class` will not be included in the returned list.
        modules (string, module, or list): If `modules` is passed, we only
            return classes that are in the given module/modules. This can be
            used to exclude base classes that come from external libraries.

    Returns:
        list: A list of `str` attribute names for every `class` in the
            inheritance hierarchy.
    """
    superclasses = collect_superclasses(cls, terminal_class, modules)
    attr_names = set()
    for superclass in superclasses:
        attr_names.update(superclass.__dict__.keys())
    return list(attr_names)


def hoist_submodules(package, extend_all=True):
    """
    Sets `__all__` attrs from submodules of `package` as attrs on `package`.

    Note:
        This only considers attributes exported by `__all__`. If a submodule
        does not define `__all__`, then it is ignored.

    Effectively does::

        from package.* import *

    Args:
        package (str or module): The parent package into which submodule
            exports should be hoisted.
        extend_all (bool): If True, `package.__all__` will be extended
            to include the hoisted attributes. Defaults to True.

    Returns:
        list: List of all hoisted attribute names.

    """
    module = resolve(package)
    hoisted_attrs = []
    for submodule in import_submodules(module):
        for attr_name, attr in import_star(submodule).items():
            hoisted_attrs.append(attr_name)
            setattr(module, attr_name, attr)

    if extend_all:
        if getattr(module, "__all__", None) is None:
            module.__all__ = list(hoisted_attrs)
        else:
            module.__all__.extend(hoisted_attrs)

    return hoisted_attrs


def import_star(module):
    """
    Imports all exported attributes of `module` and returns them in a `dict`.

    Note:
        This only considers attributes exported by `__all__`. If `module`
        does not define `__all__`, then nothing is imported.

    Effectively does::

        from module import *

    Args:
        module (str or module): The module from which a wildcard import
            should be done.

    Returns:
        dict: Map of all imported attributes.

    """
    module = resolve(module)
    attrs = getattr(module, "__all__", [])
    return dict([(attr, getattr(module, attr)) for attr in attrs])


def import_submodules(package):
    """
    Imports all submodules of `package`.

    Effectively does::

        __import__(package.*)

    Args:
        package (str or module): The parent package from which submodules
            should be imported.

    Yields:
        module: The next submodule of `package`.

    """
    module = resolve(package)
    if basename(module.__file__).startswith("__init__.py"):
        for _, submodule_name, _ in iter_modules(module.__path__):
            yield resolve(submodule_name, module)


def is_data(obj):
    """
    Returns True if `obj` is a "data like" object.

    Strongly inspired by `inspect.classify_class_attrs`. This function is
    useful when trying to determine if an attribute has a meaningful docstring
    or not. In general, a routine can have meaningful docstrings, whereas
    non-routines cannot.

    See Also:
        * `inspect.classify_class_attrs`
        * `inspect.isroutine`

    Args:
        obj (object): The object in question.

    Returns:
        bool: True if `obj` is "data like", False otherwise.
    """
    if isinstance(
        obj, (staticmethod, classmethod, property)
    ) or inspect.isroutine(obj):
        return False
    else:
        return True


def resolve(name, modules=None):
    """
    Resolve a dotted name to an object (usually class, module, or function).

    If `name` is a string, attempt to resolve it according to Python
    dot notation, e.g. "path.to.MyClass". If `name` is anything other than a
    string, return it immediately:

    >>> resolve("calendar.TextCalendar")
    <class 'calendar.TextCalendar'>
    >>> resolve(object())
    <object object at 0x...>

    If `modules` is specified, then resolution of `name` is restricted
    to the given modules. Leading dots are allowed in `name`, but they are
    ignored. Resolution **will not** traverse up the module path if `modules`
    is specified.

    If `modules` is not specified and `name` has leading dots, then resolution
    is first attempted relative to the calling function's module, and then
    absolutely. Resolution **will** traverse up the module path. If `name` has
    no leading dots, resolution is first attempted absolutely and then
    relative to the calling module.

    Pass an empty string for `modules` to only use absolute resolution.

    Warning:
        Do not resolve strings supplied by an end user without specifying
        `modules`. Instantiating an arbitrary object specified by an end user
        can introduce a potential security risk.

        To avoid this, restrict the search path by explicitly specifying
        `modules`.

    Restricting `name` resolution to a set of `modules`:

    >>> resolve("pockets.camel")
    <function camel at 0x...>
    >>> resolve("pockets.camel", modules=["re", "six"])
    Traceback (most recent call last):
    ValueError: Unable to resolve 'pockets.camel' in modules: ['re', 'six']
      ...

    Args:
        name (str or object): A dotted name.

        modules (str, module, or list, optional): A module or list of modules,
            under which to search for `name`.

    Returns:
        object: The object specified by `name`.

    Raises:
        ValueError: If `name` can't be resolved.

    """
    if not isinstance(name, string_types):
        return name

    obj_path = splitify(name, ".", include_empty=True)
    search_paths = []
    if modules is not None:
        while not obj_path[0].strip():
            obj_path.pop(0)
        for module_path in listify(modules):
            search_paths.append(splitify(module_path, ".") + obj_path)
    else:
        caller = inspect.getouterframes(inspect.currentframe())[1][0].f_globals
        module_path = caller["__name__"].split(".")
        if not obj_path[0]:
            obj_path.pop(0)
            while not obj_path[0]:
                obj_path.pop(0)
                if module_path:
                    module_path.pop()

            search_paths.append(module_path + obj_path)
            search_paths.append(obj_path)
        else:
            search_paths.append(obj_path)
            search_paths.append(module_path + obj_path)

    exceptions = []
    for path in search_paths:
        # Import the most deeply nested module available
        module = None
        module_path = []
        obj_path = list(path)
        while obj_path:
            module_name = obj_path.pop(0)
            while not module_name:
                module_name = obj_path.pop(0)
            if isinstance(module_name, string_types):
                package = ".".join(module_path + [module_name])
                try:
                    module = __import__(package, fromlist=module_name)
                except ImportError as ex:
                    exceptions.append(ex)
                    obj_path = [module_name] + obj_path
                    break
                else:
                    module_path.append(module_name)
            else:
                module = module_name
                module_path.append(module.__name__)

        if module:
            if obj_path:
                try:
                    return functools.reduce(getattr, obj_path, module)
                except AttributeError as ex:
                    exceptions.append(ex)
            else:
                return module

    if modules:
        msg = "Unable to resolve '{0}' in modules: {1}".format(name, modules)
    else:
        msg = "Unable to resolve '{0}'".format(name)

    if exceptions:
        msgs = ["{0}: {1}".format(type(e).__name__, e) for e in exceptions]
        raise ValueError("\n    ".join([msg] + msgs))
    else:
        raise ValueError(msg)


def unwrap(func):
    """
    Finds the innermost function that has been wrapped using `functools.wrap`.

    Note:
        This function relies on the existence of the `__wrapped__` attribute,
        which was not automatically added until Python 3.2. If you are using
        an older version of Python, you'll have to manually add the
        `__wrapped__` attribute in order to use `unwrap`::

            def my_decorator(func):
                @wraps(func)
                def with_my_decorator(*args, **kwargs):
                    return func(*args, **kwargs)

                if not hasattr(with_my_decorator, '__wrapped__'):
                    with_my_decorator.__wrapped__ = func

                return with_my_decorator

    Args:
        func (function): A function that may or may not have been wrapped
            using `functools.wrap`.

    Returns:
        function: The original function before it was wrapped using
            `functools.wrap`. `func` is returned directly, if it was never
            wrapped using `functools.wrap`.
    """
    return unwrap(func.__wrapped__) if hasattr(func, "__wrapped__") else func
