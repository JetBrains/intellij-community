# -*- coding: utf-8 -*-
"""
    sphinxcontrib.napoleon._upstream
    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    Functions to help compatibility with upstream sphinx.ext.napoleon.

    :copyright: Copyright 2013-2018 by Rob Ruana, see AUTHORS.
    :license: BSD, see LICENSE for details.
"""


#  Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

def _(message, *args):
    """
    NOOP implementation of sphinx.locale.get_translation shortcut.
    """
    return message
