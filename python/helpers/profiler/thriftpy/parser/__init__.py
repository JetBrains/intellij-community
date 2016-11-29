# -*- coding: utf-8 -*-

"""
    thriftpy.parser
    ~~~~~~~~~~~~~~~

    Thrift parser using ply
"""

from __future__ import absolute_import

import os
import sys

from .parser import parse, parse_fp


def load(path, module_name=None, include_dirs=None, include_dir=None):
    """Load thrift file as a module.

    The module loaded and objects inside may only be pickled if module_name
    was provided.

    Note: `include_dir` will be depreacated in the future, use `include_dirs`
    instead. If `include_dir` was provided (not None), it will be appended to
    `include_dirs`.
    """
    real_module = bool(module_name)
    thrift = parse(path, module_name, include_dirs=include_dirs,
                   include_dir=include_dir)

    if real_module:
        sys.modules[module_name] = thrift
    return thrift


def load_fp(source, module_name):
    """Load thrift file like object as a module.
    """
    thrift = parse_fp(source, module_name)
    sys.modules[module_name] = thrift
    return thrift


def _import_module(import_name):
    if '.' in import_name:
        module, obj = import_name.rsplit('.', 1)
        return getattr(__import__(module, None, None, [obj]), obj)
    else:
        return __import__(import_name)


def load_module(fullname):
    """Load thrift_file by fullname, fullname should have '_thrift' as
    suffix.
    The loader will replace the '_thrift' with '.thrift' and use it as
    filename to locate the real thrift file.
    """
    if not fullname.endswith("_thrift"):
        raise ImportError(
            "ThriftPy can only load module with '_thrift' suffix")

    if fullname in sys.modules:
        return sys.modules[fullname]

    if '.' in fullname:
        module_name, thrift_module_name = fullname.rsplit('.', 1)
        module = _import_module(module_name)
        path_prefix = os.path.dirname(os.path.abspath(module.__file__))
        path = os.path.join(path_prefix, thrift_module_name)
    else:
        path = fullname
    thrift_file = "{0}.thrift".format(path[:-7])

    module = load(thrift_file, module_name=fullname)
    sys.modules[fullname] = module
    return sys.modules[fullname]
