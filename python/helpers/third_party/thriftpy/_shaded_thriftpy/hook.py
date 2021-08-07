# -*- coding: utf-8 -*-

from __future__ import absolute_import

import sys

from .parser import load_module


class ThriftImporter(object):
    def __init__(self, extension="_thrift"):
        self.extension = extension

    def __eq__(self, other):
        return self.__class__.__module__ == other.__class__.__module__ and \
            self.__class__.__name__ == other.__class__.__name__ and \
            self.extension == other.extension

    def find_module(self, fullname, path=None):
        if fullname.endswith(self.extension):
            return self

    def load_module(self, fullname):
        return load_module(fullname)


_imp = ThriftImporter()


def install_import_hook():
    global _imp
    sys.meta_path[:] = [x for x in sys.meta_path if _imp != x] + [_imp]


def remove_import_hook():
    global _imp
    sys.meta_path[:] = [x for x in sys.meta_path if _imp != x]
