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
        TException.__init__(self, message)
        self.type = type
