# -*- coding: utf-8 -*-
# Copyright (c) 2016 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""*Let me check my pockets...*

Functions available in the `pockets.*` submodules are also imported to the base
package for easy access, so::

    from pockets import camel, peek_iter, resolve

works just as well as::

    from pockets.inspect import resolve
    from pockets.iterators import peek_iter
    from pockets.string import camel

"""

from __future__ import absolute_import
from pockets._version import __version__
from pockets.collections import is_listy, listify, mappify
from pockets.inspect import resolve
from pockets.iterators import peek_iter, modify_iter
from pockets.string import camel, uncamel, splitcaps, UnicodeMixin


__all__ = ["__version__",
           "camel",
           "uncamel",
           "splitcaps",
           "UnicodeMixin",
           "resolve",
           "is_listy",
           "listify",
           "mappify",
           "peek_iter",
           "modify_iter"]
