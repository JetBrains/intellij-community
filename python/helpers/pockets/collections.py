# -*- coding: utf-8 -*-
# Copyright (c) 2016 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""A pocket full of useful collection functions!"""

from __future__ import absolute_import
from collections import Sized, Iterable, Mapping
from inspect import isclass

import six

__all__ = ["is_listy", "listify", "mappify"]


def is_listy(x):
    """Return True if `x` is "listy", i.e. a list-like object.

    "Listy" is defined as a sized iterable which is neither a map nor a string:

    >>> is_listy(["a", "b"])
    True
    >>> is_listy(set())
    True
    >>> is_listy(iter(["a", "b"]))
    False
    >>> is_listy({"a": "b"})
    False
    >>> is_listy("a regular string")
    False

    Note:
        Iterables and generators fail the "listy" test because they
        are not sized.

    Args:
        x (any value): The object to test.

    Returns:
        bool: True if `x` is "listy", False otherwise.

    """
    return (isinstance(x, Sized) and
            isinstance(x, Iterable) and
            not isinstance(x, Mapping) and
            not isinstance(x, six.string_types))


def listify(x, minlen=0, default=None, cls=None):
    """Return a listified version of `x`.

    If `x` is a non-string iterable, it is wrapped in a list; otherwise
    a list is returned with `x` as its only element.

    >>> listify("a regular string")
    ['a regular string']
    >>> listify(tuple(["a", "b", "c"]))
    ['a', 'b', 'c']
    >>> listify({'a': 'A'})
    [{'a': 'A'}]

    Note:
        Not guaranteed to return a copy of `x`. If `x` is already a list and
        `cls` is not specified, then `x` itself is returned.

    Args:
        x (any value): Value to listify.

        minlen (int): Minimum length of the returned list. If the returned
            list would be shorter than `minlen` it is padded with values from
            `default`. Defaults to 0.

            >>> listify([], minlen=0)
            []
            >>> listify([], minlen=1)
            [None]
            >>> listify("item", minlen=3)
            ['item', None, None]

        default (any value): Value that should be used to pad the list if it
            would be shorter than `minlen`:

            >>> listify([], minlen=1, default="PADDING")
            ['PADDING']
            >>> listify("item", minlen=3, default="PADDING")
            ['item', 'PADDING', 'PADDING']

        cls (class or callable): Instead of wrapping `x` in a list, wrap it
            in an instance of `cls`. `cls` should accept an iterable object
            as its single parameter when called:

            >>> from collections import deque
            >>> listify(["a", "b", "c"], cls=deque)
            deque(['a', 'b', 'c'])

    Returns:
        list or `cls`: A listified version of `x`.

    """
    if x is None:
        x = []
    elif not isinstance(x, list):
        x = list(x) if is_listy(x) else [x]

    if minlen and len(x) < minlen:
        x.extend([default for i in range(minlen - len(x))])

    if cls and not (isclass(cls) and issubclass(type(x), cls)):
        x = cls(x)
    return x


def mappify(x, default=True, cls=None):
    """Return a mappified version of `x`.

    If `x` is a string, it becomes the only key of the returned dict. If `x`
    is a non-string iterable, the elements of `x` become keys in the returned
    dict. The values of the returned dict are set to `default`.

    If `x` is a map, it is returned directly.

    >>> mappify("a regular string")
    {'a regular string': True}
    >>> mappify(["a"])
    {'a': True}
    >>> mappify({'a': "A"})
    {'a': 'A'}

    Note:
        Not guaranteed to return a copy of `x`. If `x` is already a map and
        `cls` is not specified, then `x` itself is returned.

    Args:
        x (str, map, or iterable): Value to mappify.

        default (any value): Value used to fill out missing values of the
            returned dict.

        cls (class or callable): Instead of wrapping `x` in a dict, wrap it
            in an instance of `cls`. `cls` should accept a map object as
            its single parameter when called:

            >>> from collections import defaultdict
            >>> mappify("a", cls=lambda x: defaultdict(None, x))
            defaultdict(None, {'a': True})

    Returns:
        dict or `cls`: A mappified version of `x`.

    Raises:
        TypeError: If `x` is not a map, iterable, or string.

    """
    if not isinstance(x, Mapping):
        if isinstance(x, six.string_types):
            x = {x: default}
        elif isinstance(x, Iterable):
            x = dict([(v, default) for v in x])
        else:
            raise TypeError("Unable to mappify {0}".format(type(x)), x)

    if cls and not (isclass(cls) and issubclass(type(x), cls)):
        x = cls(x)
    return x
