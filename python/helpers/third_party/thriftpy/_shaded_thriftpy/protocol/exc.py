# -*- coding: utf-8 -*-

from __future__ import absolute_import

from ..thrift import TException


class TProtocolException(TException):
    """Custom Protocol Exception class"""

    UNKNOWN = 0
    INVALID_DATA = 1
    NEGATIVE_SIZE = 2
    SIZE_LIMIT = 3
    BAD_VERSION = 4

    def __init__(self, type=UNKNOWN, message=None):
        self.type = type
        self.message = message

    def __str__(self):
        if self.message:
            return self.message

        if self.type == self.UNKNOWN:
            return 'Unknown protocol exception'
        elif self.type == self.INVALID_DATA:
            return 'Invalid data'
        elif self.type == self.NEGATIVE_SIZE:
            return 'Negative size'
        elif self.type == self.SIZE_LIMIT:
            return 'Size limit'
        elif self.type == self.BAD_VERSION:
            return 'Bad version'
        else:
            return 'Default (unknown) TProtocolException'
