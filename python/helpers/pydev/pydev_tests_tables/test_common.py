#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
"""
Here we check common requirements for each framework support.
We check:
 1. a proper file naming
 2. all needed methods are existed in corresponding files

"""

import os
import sys
import pytest
import inspect
import importlib


@pytest.fixture
def setup_tables_modules():
    pydevd_tables_path = '../_pydevd_bundle/tables'
    tables_files, tables_modules = [], []

    for _, _, files in os.walk(pydevd_tables_path):
        for file in files:
            if file.endswith('py') and file != '__init__.py':
                if sys.version_info < (3, 0) and 'polars' in file:
                    # we don't need to test polars for python 2.7
                    continue
                tables_files.append(file)
                module_name = '_pydevd_bundle.tables.' + file.replace('.py', '')
                tables_modules.append(importlib.import_module(module_name))

    return tables_files, tables_modules


def test_all_helpers_methods(setup_tables_modules):
    """
    Check that all needed methods are existed in the corresponding file.
    :param setup_tables_modules: fixture/data for the test
    """
    _, modules = setup_tables_modules
    for module in modules:
        members = inspect.getmembers(module)
        methods = [member[0] for member in members if inspect.isfunction(member[1])]

        # methods for getInfoCommand:
        assert 'get_type' in methods
        assert 'get_shape' in methods
        assert 'get_head' in methods
        assert 'get_column_types' in methods

        # methods for slice commands:
        assert 'get_data' in methods
        assert 'display_data' in methods


def test_proper_file_naming(setup_tables_modules):
    """
    Check files' naming.
    :param setup_tables_modules: fixture/data for the test
    """
    files, _ = setup_tables_modules

    for file in files:
        assert file.startswith('pydevd')
