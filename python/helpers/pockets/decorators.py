# -*- coding: utf-8 -*-
# Copyright (c) 2018 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""A pocket full of useful decorators!"""

from __future__ import absolute_import, print_function

import inspect
from functools import wraps

from pockets.collections import listify
from pockets.inspect import unwrap


__all__ = [
    "argmod",
    "cached_classproperty",
    "cached_property",
    "classproperty",
]


def argmod(*args):
    """
    Decorator that intercepts and modifies function arguments.

    Args:
        from_param (str|list): A parameter or list of possible parameters that
            should be modified using `modifier_func`. Passing a list of
            possible parameters is useful when a function's parameter names
            have changed, but you still want to support the old parameter
            names.
        to_param (str): Optional. If given, to_param will be used as the
            parameter name for the modified argument. If not given, to_param
            will default to the last parameter given in `from_param`.
        modifier_func (callable): The function used to modify the `from_param`.

    Returns:
        function: A function that modifies the given `from_param` before the
            function is called.
    """
    from_param = listify(args[0])
    to_param = from_param[-1] if len(args) < 3 else args[1]
    modifier_func = args[-1]

    def _decorator(func):
        try:
            argspec = inspect.getfullargspec(unwrap(func))
        except AttributeError:
            argspec = inspect.getargspec(unwrap(func))
        if to_param not in argspec.args:
            return func
        arg_index = argspec.args.index(to_param)

        @wraps(func)
        def _modifier(*args, **kwargs):
            kwarg = False
            for arg in from_param:
                if arg in kwargs:
                    kwarg = arg
                    break

            if kwarg:
                kwargs[to_param] = modifier_func(kwargs.pop(kwarg))
            elif arg_index < len(args):
                args = list(args)
                args[arg_index] = modifier_func(args[arg_index])
            return func(*args, **kwargs)

        return _modifier

    return _decorator


class cached_classproperty(property):
    """
    Like @cached_property except it works on classes instead of instances.


    Note:
        Class properties created by @cached_classproperty are read-only.
        Any attempts to write to the property will erase the
        @cached_classproperty, and the behavior of the underlying method
        will be lost.

    >>> class MyClass(object):
    ...     @cached_classproperty
    ...     def myproperty(cls):
    ...         return '{0}.myproperty'.format(cls.__name__)
    >>> MyClass.myproperty
    'MyClass.myproperty'

    """

    def __init__(self, fget, *arg, **kw):
        super(cached_classproperty, self).__init__(fget, *arg, **kw)
        self.__doc__ = fget.__doc__
        self.__fget_name__ = fget.__name__

    def __get__(desc, self, cls):
        cache_attr = "_cached_{0}_{1}".format(cls.__name__, desc.__fget_name__)
        if not hasattr(cls, cache_attr):
            setattr(cls, cache_attr, desc.fget(cls))
        return getattr(cls, cache_attr)

    def getter(self, fget):
        raise AttributeError("@cached_classproperty.getter is not supported")

    def setter(self, fset):
        raise AttributeError("@cached_classproperty.setter is not supported")

    def deleter(self, fdel):
        raise AttributeError("@cached_classproperty.deleter is not supported")


def cached_property(func):
    """Decorator for making readonly, memoized properties."""
    cache_attr = "_cached_{0}".format(func.__name__)

    @property
    @wraps(func)
    def caching(self, *args, **kwargs):
        if not hasattr(self, cache_attr):
            setattr(self, cache_attr, func(self, *args, **kwargs))
        return getattr(self, cache_attr)

    return caching


class classproperty(property):
    """
    Decorator to create a read-only class property similar to classmethod.

    For whatever reason, the @property decorator isn't smart enough to
    recognize @classmethods and behaves differently on them than on instance
    methods.  This decorator may be used like to create a class-level property,
    useful for singletons and other one-per-class properties.

    This implementation is partially based on
    `sqlalchemy.util.langhelpers.classproperty`.

    Note:
        Class properties created by @classproperty are read-only. Any attempts
        to write to the property will erase the @classproperty, and the
        behavior of the underlying method will be lost.

    >>> class MyClass(object):
    ...     @classproperty
    ...     def myproperty(cls):
    ...         return '{0}.myproperty'.format(cls.__name__)
    >>> MyClass.myproperty
    'MyClass.myproperty'

    """

    def __init__(self, fget, *arg, **kw):
        super(classproperty, self).__init__(fget, *arg, **kw)
        self.__doc__ = fget.__doc__

    def __get__(desc, self, cls):
        return desc.fget(cls)

    def getter(self, fget):
        raise AttributeError("@classproperty.getter is not supported")

    def setter(self, fset):
        raise AttributeError("@classproperty.setter is not supported")

    def deleter(self, fdel):
        raise AttributeError("@classproperty.deleter is not supported")
