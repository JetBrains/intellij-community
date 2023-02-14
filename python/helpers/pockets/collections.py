# -*- coding: utf-8 -*-
# Copyright (c) 2018 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""A pocket full of useful collection tools!"""

from __future__ import absolute_import, print_function

from collections import defaultdict
from inspect import isclass

try:
    from collections.abc import Iterable, Mapping, Sized
except ImportError:
    from collections import Iterable, Mapping, Sized
try:
    from collections import OrderedDict
except ImportError:
    OrderedDict = dict

import six


__all__ = [
    "groupify",
    "keydefaultdict",
    "is_listy",
    "listify",
    "is_mappy",
    "mappify",
    "nesteddefaultdict",
    "readable_join",
    "uniquify",
]


def groupify(items, keys, val_key=None):
    """
    Groups a list of items into nested OrderedDicts based on the given keys.

    Note:
        On Python 2.6 the return value will use regular dicts instead of
        OrderedDicts.

    >>> from __future__ import print_function
    >>> from json import dumps
    >>>
    >>> ex = lambda x: print(dumps(x, indent=2, sort_keys=True, default=repr))
    >>>
    >>> class Reminder:
    ...   def __init__(self, when, where, what):
    ...     self.when = when
    ...     self.where = where
    ...     self.what = what
    ...   def __repr__(self):
    ...     return 'Reminder({0.when}, {0.where}, {0.what})'.format(self)
    ...
    >>> reminders = [
    ...   Reminder('Fri', 'Home', 'Eat cereal'),
    ...   Reminder('Fri', 'Work', 'Feed Ivan'),
    ...   Reminder('Sat', 'Home', 'Sleep in'),
    ...   Reminder('Sat', 'Home', 'Play Zelda'),
    ...   Reminder('Sun', 'Home', 'Sleep in'),
    ...   Reminder('Sun', 'Work', 'Reset database')]
    >>>
    >>> ex(groupify(reminders, 'when'))
    {
      "Fri": [
        "Reminder(Fri, Home, Eat cereal)",
        "Reminder(Fri, Work, Feed Ivan)"
      ],
      "Sat": [
        "Reminder(Sat, Home, Sleep in)",
        "Reminder(Sat, Home, Play Zelda)"
      ],
      "Sun": [
        "Reminder(Sun, Home, Sleep in)",
        "Reminder(Sun, Work, Reset database)"
      ]
    }
    >>>
    >>> ex(groupify(reminders, ['when', 'where']))
    {
      "Fri": {
        "Home": [
          "Reminder(Fri, Home, Eat cereal)"
        ],
        "Work": [
          "Reminder(Fri, Work, Feed Ivan)"
        ]
      },
      "Sat": {
        "Home": [
          "Reminder(Sat, Home, Sleep in)",
          "Reminder(Sat, Home, Play Zelda)"
        ]
      },
      "Sun": {
        "Home": [
          "Reminder(Sun, Home, Sleep in)"
        ],
        "Work": [
          "Reminder(Sun, Work, Reset database)"
        ]
      }
    }
    >>>
    >>> ex(groupify(reminders, ['when', 'where'], 'what'))
    {
      "Fri": {
        "Home": [
          "Eat cereal"
        ],
        "Work": [
          "Feed Ivan"
        ]
      },
      "Sat": {
        "Home": [
          "Sleep in",
          "Play Zelda"
        ]
      },
      "Sun": {
        "Home": [
          "Sleep in"
        ],
        "Work": [
          "Reset database"
        ]
      }
    }
    >>>
    >>> ex(groupify(reminders, lambda r: '{0.when} - {0.where}'.format(r), 'what'))
    {
      "Fri - Home": [
        "Eat cereal"
      ],
      "Fri - Work": [
        "Feed Ivan"
      ],
      "Sat - Home": [
        "Sleep in",
        "Play Zelda"
      ],
      "Sun - Home": [
        "Sleep in"
      ],
      "Sun - Work": [
        "Reset database"
      ]
    }

    Args:
        items (list): The list of items to arrange in groups.
        keys (str|callable|list): The key or keys that should be used to group
            `items`. If multiple keys are given, then each will correspond to
            an additional level of nesting in the order they are given.
        val_key (str|callable): A key or callable used to generate the leaf
            values in the nested OrderedDicts. If `val_key` is `None`, then
            the item itself is used. Defaults to `None`.

    Returns:
        OrderedDict: Nested OrderedDicts with `items` grouped by `keys`.

    """  # noqa: E501

    if not keys:
        return items
    keys = listify(keys)
    last_key = keys[-1]
    is_callable = callable(val_key)
    groupified = OrderedDict()
    for item in items:
        current = groupified
        for key in keys:
            attr = key(item) if callable(key) else getattr(item, key)
            if attr not in current:
                current[attr] = [] if key is last_key else OrderedDict()
            current = current[attr]
        if val_key:
            value = val_key(item) if is_callable else getattr(item, val_key)
        else:
            value = item
        current.append(value)
    return groupified


class keydefaultdict(defaultdict):
    """
    A defaultdict that passes the missed key to the factory function.

    >>> def echo_factory(missing_key):
    ...     return missing_key
    ...
    >>> d = keydefaultdict(echo_factory)
    >>> d['Hello World']
    'Hello World'
    >>> d['Hello World'] = 'Goodbye'
    >>> d['Hello World']
    'Goodbye'

    """

    def __missing__(self, key):
        if self.default_factory is None:
            raise KeyError(key)
        else:
            ret = self[key] = self.default_factory(key)
            return ret


def is_listy(x):
    """
    Return True if `x` is "listy", i.e. a list-like object.

    "Listy" is defined as a sized iterable which is neither a map nor a string:

    >>> is_listy(['a', 'b'])
    True
    >>> is_listy(set())
    True
    >>> is_listy(iter(['a', 'b']))
    False
    >>> is_listy({'a': 'b'})
    False
    >>> is_listy('a regular string')
    False

    Note:
        Iterables and generators fail the "listy" test because they
        are not sized.

    Args:
        x (any value): The object to test.

    Returns:
        bool: True if `x` is "listy", False otherwise.

    """
    return (
        isinstance(x, Sized)
        and isinstance(x, Iterable)
        and not isinstance(x, (Mapping, type(b"")))
        and not isinstance(x, six.string_types)
    )


def listify(x, minlen=0, default=None, cls=None):
    """
    Return a listified version of `x`.

    If `x` is a non-string iterable, it is wrapped in a list; otherwise
    a list is returned with `x` as its only element. If `x` is `None`, an
    empty list is returned.

    >>> listify('a regular string')
    ['a regular string']
    >>> listify(tuple(['a', 'b', 'c']))
    ['a', 'b', 'c']
    >>> listify({'a': 'A'})
    [{'a': 'A'}]
    >>> listify(None)
    []

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
            >>> listify('item', minlen=3)
            ['item', None, None]

        default (any value): Value that should be used to pad the list if it
            would be shorter than `minlen`:

            >>> listify([], minlen=1, default='PADDING')
            ['PADDING']
            >>> listify('item', minlen=3, default='PADDING')
            ['item', 'PADDING', 'PADDING']

        cls (class or callable): Instead of wrapping `x` in a list, wrap it
            in an instance of `cls`. `cls` should accept an iterable object
            as its single parameter when called:

            >>> from collections import deque
            >>> listify(['a', 'b', 'c'], cls=deque)
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


def is_mappy(x):
    """
    Return True if `x` is "mappy", i.e. a map-like object.

    "Mappy" is defined as any instance of `collections.Mapping`:

    >>> is_mappy({'a': 'b'})
    True
    >>> from collections import defaultdict
    >>> is_mappy(defaultdict(list))
    True
    >>> is_mappy('a regular string')
    False
    >>> is_mappy(['a', 'b'])
    False
    >>> is_listy(iter({'a': 'b'}))
    False

    Note:
        Iterables and generators fail the "mappy" test.

    Args:
        x (any value): The object to test.

    Returns:
        bool: True if `x` is "mappy", False otherwise.

    """
    return isinstance(x, Mapping)


def mappify(x, default=True, cls=None):
    """
    Return a mappified version of `x`.

    If `x` is a string, it becomes the only key of the returned dict. If `x`
    is a non-string iterable, the elements of `x` become keys in the returned
    dict. The values of the returned dict are set to `default`. If `x` is
    `None`, an empty dict is returned.

    If `x` is a map, it is returned directly.

    >>> mappify('a regular string')
    {'a regular string': True}
    >>> mappify(['a'])
    {'a': True}
    >>> mappify({'a': 'A'})
    {'a': 'A'}
    >>> mappify(None)
    {}

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
            >>> mappify('a', cls=lambda x: defaultdict(None, x))
            defaultdict(None, {'a': True})

    Returns:
        dict or `cls`: A mappified version of `x`.

    Raises:
        TypeError: If `x` is not a map, iterable, or string.

    """
    if x is None:
        x = {}
    elif not isinstance(x, Mapping):
        if isinstance(x, six.string_types):
            x = {x: default}
        elif isinstance(x, Iterable):
            # If cls is specified, attempt to preserve the order of x, in
            # case cls is also a class that preserves order.
            arg = [(v, default) for v in x]
            x = OrderedDict(arg) if cls else dict(arg)
        else:
            raise TypeError(
                "Unable to mappify non-mappy {0}".format(type(x)), x
            )

    if cls and not (isclass(cls) and issubclass(type(x), cls)):
        x = cls(x)
    return x


def nesteddefaultdict():
    """
    A defaultdict that returns nested defaultdicts as the default value.

    Each defaultdict returned as the default value will also return nested
    defaultdicts, and so on.

    >>> nested = nesteddefaultdict()
    >>> nested_child = nested['New Key 1']
    >>> nested_child
    defaultdict(...)
    >>> nested_grandchild = nested_child['New Key 2']
    >>> nested_grandchild
    defaultdict(...)

    """
    return defaultdict(nesteddefaultdict)


def readable_join(xs, conjunction="and", sep=","):
    """
    Accepts a list of strings and separates them with commas as grammatically
    appropriate with a conjunction before the final entry. Any input strings
    containing only whitespace will not be included in the result.

    >>> readable_join(['foo'])
    'foo'
    >>> readable_join(['foo', 'bar'])
    'foo and bar'
    >>> readable_join(['foo', 'bar', 'baz'])
    'foo, bar, and baz'
    >>> readable_join(['foo', '  ', '', 'bar', '', '  ', 'baz'])
    'foo, bar, and baz'
    >>> readable_join(['foo', 'bar', 'baz'], 'or')
    'foo, bar, or baz'
    >>> readable_join(['foo', 'bar', 'baz'], 'but never')
    'foo, bar, but never baz'

    """
    xs = [s for s in map(lambda s: str(s).strip(), listify(xs)) if s]
    if len(xs) > 1:
        xs = list(xs)
        xs[-1] = conjunction + " " + xs[-1]
    return (sep + " " if len(xs) > 2 else " ").join(xs)


def uniquify(x, key=lambda o: o, cls=None):
    """
    Returns an order-preserved copy of `x` with duplicate items removed.

    >>> uniquify(['a', 'z', 'a', 'b', 'a', 'y', 'a', 'c', 'a', 'x'])
    ['a', 'z', 'b', 'y', 'c', 'x']

    Args:
        x (Sequence): Sequence to uniquify.

        key (str or callable): Similar to `sorted`, specifies an attribute or
            function of one argument that is used to extract a comparison key
            from each list element: key=str.lower. By default, compares the
            elements directly.

            >>> strings = ['ASDF', 'asdf', 'ZXCV', 'zxcv']
            >>> uniquify(strings, key=str.lower)
            ['ASDF', 'ZXCV']

        cls (class or callable): Instead of wrapping `x` in a list, wrap it
            in an instance of `cls`. `cls` should accept an iterable object
            as its single parameter when called:

            >>> from collections import deque
            >>> listify(['a', 'b', 'c'], cls=deque)
            deque(['a', 'b', 'c'])

    Returns:
        list: An order-preserved copy of `x` with duplicate items removed.

    Raises:
        TypeError: If `x` is not "listy".

    """
    if not is_listy(x):
        raise TypeError("Unable to uniquify non-listy {0}".format(type(x)), x)
    seen = set()
    keys = [(key(o) if callable(key) else getattr(o, key), o) for o in x]
    x = [o for k, o in keys if k not in seen and not seen.add(k)]

    if cls and not (isclass(cls) and issubclass(type(x), cls)):
        x = cls(x)
    return x
