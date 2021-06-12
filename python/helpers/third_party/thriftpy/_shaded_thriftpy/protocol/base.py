# -*- coding: utf-8 -*-


class TProtocolBase(object):
    """Base class for Thrift protocol layer."""

    def __init__(self, trans):
        self.trans = trans  # transport is public and used by TClient

    def skip(self, ttype):
        raise NotImplementedError

    def read_message_begin(self):
        raise NotImplementedError

    def read_message_end(self):
        raise NotImplementedError

    def write_message_begin(self, name, ttype, seqid):
        raise NotImplementedError

    def write_message_end(self):
        raise NotImplementedError

    def read_struct(self, obj):
        raise NotImplementedError

    def write_struct(self, obj):
        raise NotImplementedError
