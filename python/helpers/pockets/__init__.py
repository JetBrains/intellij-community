# -*- coding: utf-8 -*-
# Copyright (c) 2018 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""
*Let me check my pockets...*

Functions available in the `pockets.*` submodules are also imported to the base
package for easy access, so::

    from pockets import camel, iterpeek, resolve

works just as well as::

    from pockets.inspect import resolve
    from pockets.iterators import iterpeek
    from pockets.string import camel

"""

from __future__ import absolute_import, print_function

import sys

from pockets.inspect import hoist_submodules


hoist_submodules(sys.modules[__name__])
