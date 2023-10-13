# encoding: utf-8
"""
Utilities for version comparison

It is a bit ridiculous that we need these.
"""

#-----------------------------------------------------------------------------
#  Copyright (C) 2013  The IPython Development Team
#
#  Distributed under the terms of the BSD License.  The full license is in
#  the file COPYING, distributed as part of this software.
#-----------------------------------------------------------------------------

#-----------------------------------------------------------------------------
# Imports
#-----------------------------------------------------------------------------

try:
    from distutils.version import LooseVersion
except ImportError:
    import sys
    if sys.version_info[:2] >= (3, 12):
        LooseVersion = lambda v: v.split('.')
    else:
        raise

#-----------------------------------------------------------------------------
# Code
#-----------------------------------------------------------------------------

def check_version(v, check):
    """check version string v >= check

    If dev/prerelease tags result in TypeError for string-number comparison,
    it is assumed that the dependency is satisfied.
    Users on dev branches are responsible for keeping their own packages up to date.
    """
    try:
        return LooseVersion(v) >= LooseVersion(check)
    except TypeError:
        return True

