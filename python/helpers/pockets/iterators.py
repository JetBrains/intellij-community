# -*- coding: utf-8 -*-
# Copyright (c) 2016 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""A pocket full of useful iterators!"""

from __future__ import absolute_import
import collections

import six

__all__ = ["peek_iter", "modify_iter"]


class peek_iter(object):
    """An iterator object that supports peeking ahead.

    >>> p = peek_iter(["a", "b", "c", "d", "e"])
    >>> p.peek()
    'a'
    >>> p.next()
    'a'
    >>> p.peek(3)
    ['b', 'c', 'd']

    Args:
        o (iterable or callable): `o` is interpreted very differently
            depending on the presence of `sentinel`.

            If `sentinel` is not given, then `o` must be a collection object
            which supports either the iteration protocol or the sequence
            protocol.

            If `sentinel` is given, then `o` must be a callable object.

        sentinel (any value, optional): If given, the iterator will call `o`
            with no arguments for each call to its `next` method; if the value
            returned is equal to `sentinel`, :exc:`StopIteration` will be
            raised, otherwise the value will be returned.

    See Also:
        `peek_iter` can operate as a drop in replacement for the built-in
        `iter <http://docs.python.org/2/library/functions.html#iter>`_
        function.

    Attributes:
        sentinel (any value): The value used to indicate the iterator is
            exhausted. If `sentinel` was not given when the `peek_iter` was
            instantiated, then it will be set to a new object
            instance: ``object()``.

    """
    def __init__(self, *args):
        """__init__(o, sentinel=None)"""
        self._iterable = iter(*args)
        self._cache = collections.deque()
        self.sentinel = args[1] if len(args) > 1 else object()

    def __iter__(self):
        return self

    def __next__(self, n=None):
        # NOTE: Prevent 2to3 from transforming self.next() in next(self),
        # which causes an infinite loop!
        return getattr(self, 'next')(n)

    def _fillcache(self, n):
        """Cache `n` items. If `n` is 0 or None, then 1 item is cached."""
        if not n:
            n = 1
        try:
            while len(self._cache) < n:
                self._cache.append(next(self._iterable))
        except StopIteration:
            while len(self._cache) < n:
                self._cache.append(self.sentinel)

    def has_next(self):
        """Determine if iterator is exhausted.

        Returns:
            bool: True if iterator has more items, False otherwise.

        Note:
            Will never raise :exc:`StopIteration`.

        """
        return self.peek() != self.sentinel

    def next(self, n=None):
        """Get the next item or `n` items of the iterator.

        Args:
            n (int, optional): The number of items to retrieve. Defaults to
                None.

        Returns:
            item or list of items: The next item or `n` items of the iterator.
            If `n` is None, the item itself is returned. If `n` is an int,
            the items will be returned in a list. If `n` is 0, an empty
            list is returned:

                >>> p = peek_iter(["a", "b", "c", "d", "e"])
                >>> p.next()
                'a'
                >>> p.next(0)
                []
                >>> p.next(1)
                ['b']
                >>> p.next(2)
                ['c', 'd']

        Raises:
            StopIteration: Raised if the iterator is exhausted, even if
                `n` is 0.

        """
        self._fillcache(n)
        if not n:
            if self._cache[0] == self.sentinel:
                raise StopIteration
            if n is None:
                result = self._cache.popleft()
            else:
                result = []
        else:
            if self._cache[n - 1] == self.sentinel:
                raise StopIteration
            result = [self._cache.popleft() for i in range(n)]
        return result

    def peek(self, n=None):
        """Preview the next item or `n` items of the iterator.

        The iterator is not advanced when peek is called.

        Args:
            n (int, optional): The number of items to retrieve. Defaults to
                None.

        Returns:
            item or list of items: The next item or `n` items of the iterator.
            If `n` is None, the item itself is returned. If `n` is an int,
            the items will be returned in a list. If `n` is 0, an empty
            list is returned.

            If the iterator is exhausted, `peek_iter.sentinel` is returned,
            or placed as the last item in the returned list:

                >>> p = peek_iter(["a", "b", "c"])
                >>> p.sentinel = "END"
                >>> p.peek()
                'a'
                >>> p.peek(0)
                []
                >>> p.peek(1)
                ['a']
                >>> p.peek(2)
                ['a', 'b']
                >>> p.peek(4)
                ['a', 'b', 'c', 'END']

        Note:
            Will never raise :exc:`StopIteration`.

        """
        self._fillcache(n)
        if n is None:
            result = self._cache[0]
        else:
            result = [self._cache[i] for i in range(n)]
        return result


class modify_iter(peek_iter):
    """An iterator object that supports modifying items as they are returned.

    >>> a = ["     A list    ",
    ...      "   of strings  ",
    ...      "      with     ",
    ...      "      extra    ",
    ...      "   whitespace. "]
    >>> modifier = lambda s: s.strip().replace('with', 'without')
    >>> for s in modify_iter(a, modifier=modifier):
    ...   print('"%s"' % s)
    "A list"
    "of strings"
    "without"
    "extra"
    "whitespace."

    Args:
        o (iterable or callable): `o` is interpreted very differently
            depending on the presence of `sentinel`.

            If `sentinel` is not given, then `o` must be a collection object
            which supports either the iteration protocol or the sequence
            protocol.

            If `sentinel` is given, then `o` must be a callable object.

        sentinel (any value, optional): If given, the iterator will call `o`
            with no arguments for each call to its `next` method; if the value
            returned is equal to `sentinel`, :exc:`StopIteration` will be
            raised, otherwise the value will be returned.

        modifier (callable, optional): The function that will be used to
            modify each item returned by the iterator. `modifier` should take
            a single argument and return a single value. Defaults
            to ``lambda x: x``.

            If `sentinel` is not given, `modifier` must be passed as a keyword
            argument.

    Attributes:
        modifier (callable): `modifier` is called with each item in `o` as it
            is iterated. The return value of `modifier` is returned in lieu of
            the item.

            Values returned by `peek` as well as `next` are affected by
            `modifier`. However, `modify_iter.sentinel` is never passed through
            `modifier`; it will always be returned from `peek` unmodified.

    """
    def __init__(self, *args, **kwargs):
        """__init__(o, sentinel=None, modifier=lambda x: x)"""
        if 'modifier' in kwargs:
            self.modifier = kwargs['modifier']
        elif len(args) > 2:
            self.modifier = args[2]
            args = args[:2]
        else:
            self.modifier = lambda x: x
        if not six.callable(self.modifier):
            raise TypeError('modify_iter(o, modifier): '
                            'modifier must be callable')
        super(modify_iter, self).__init__(*args)

    def _fillcache(self, n):
        """Cache `n` modified items. If `n` is 0 or None, 1 item is cached.

        Each item returned by the iterator is passed through the
        `modify_iter.modified` function before being cached.

        """
        if not n:
            n = 1
        try:
            while len(self._cache) < n:
                self._cache.append(self.modifier(next(self._iterable)))
        except StopIteration:
            while len(self._cache) < n:
                self._cache.append(self.sentinel)
