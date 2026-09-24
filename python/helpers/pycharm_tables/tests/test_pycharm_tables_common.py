#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import importlib
import inspect
import os

import pycharm_tables


def test_tables_modules_are_loaded_from_pycharm_tables():
    module = importlib.import_module('pycharm_tables.pydevd_pandas')

    assert module.__name__ == 'pycharm_tables.pydevd_pandas'
    assert os.path.dirname(module.__file__) == os.path.dirname(pycharm_tables.__file__)


def test_all_helpers_methods():
    required_functions = {
        'display_data_csv',
        'display_data_html',
        'get_column_types',
        'get_data',
        'get_head',
        'get_shape',
        'get_type',
    }

    for module in _get_tables_modules():
        for function_name in required_functions:
            assert hasattr(module, function_name)


def test_all_helpers_methods_arguments():
    required_functions = {
        'display_data_csv': 3,
        'display_data_html': 3,
        'get_column_types': 1,
        'get_data': 5,
        'get_head': 1,
        'get_shape': 1,
        'get_type': 1,
    }

    for module in _get_tables_modules():
        for function_name, parameter_count in required_functions.items():
            function = getattr(module, function_name)
            assert len(inspect.signature(function).parameters) == parameter_count


def _get_tables_modules():
    modules = []
    for file_name in os.listdir(os.path.dirname(pycharm_tables.__file__)):
        if not file_name.startswith('pydevd_') or not file_name.endswith('.py'):
            continue
        if file_name in ('pydevd_tables.py', 'pydevd_tabular_to_xml.py'):
            continue

        module = importlib.import_module('pycharm_tables.' + file_name[:-3])
        modules.append(module)
    return modules
