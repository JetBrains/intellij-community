#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import importlib
import os
import sys

helpers_directory = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
debugpy_pydevd_directory = os.path.join(helpers_directory, 'debugpy', '_vendored', 'pydevd')

sys.path.insert(0, helpers_directory)
sys.path.insert(0, debugpy_pydevd_directory)

importlib.import_module('_pydev_bundle')
