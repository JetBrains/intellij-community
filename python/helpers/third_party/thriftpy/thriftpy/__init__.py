# -*- coding: utf-8 -*-

import sys

from .hook import install_import_hook, remove_import_hook
from .parser import load, load_module, load_fp

__version__ = '0.3.8'
__python__ = sys.version_info
__all__ = ["install_import_hook", "remove_import_hook",  "load", "load_module",
           "load_fp"]
