"""Skeleton for 'collections' stdlib module."""


import sys
import collections


class Iterable(object):
    def __init__(self):
        """
        :rtype: collections.Iterable[T]
        """
        pass

    def __iter__(self):
        """
        :rtype: collections.Iterator[T]
        """


class Iterator(collections.Iterable):
    def __init__(self):
        """
        :rtype: collections.Iterator[T]
        """
        pass

    if sys.version_info >= (3, 0):
        def __next__(self):
            """
            :rtype: T
            """
            pass

    if sys.version_info < (3, 0):
        def next(self):
            """
            :rtype: T
            """
            pass
