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
        assert 'display_data_html' in methods
        assert 'display_data_csv' in methods


@pytest.mark.skipif(sys.version_info < (3, 0),
                    reason="In Python 2, we get exception - 'AttributeError: 'module' object has no attribute 'signature''")
def test_all_helpers_methods_arguments(setup_tables_modules):
    _, modules = setup_tables_modules
    one_argument_functions = ["get_type", "get_shape",
                              "get_head", "get_column_types",
                              "get_column_descriptions", "get_value_occurrences_count"]
    for module in modules:
        functions_to_its_arguments = __get_functions_and_its_arguments(module)
        for function_name, arguments in functions_to_its_arguments.items():
            if function_name in one_argument_functions:
                assert len(arguments) == 1
                assert "table" in arguments
            elif function_name in ["display_data_csv", "display_data_html"]:
                assert len(arguments) == 3
                assert "table" in arguments
                assert "start_index" in arguments
                assert "end_index" in arguments
            elif function_name == "get_data":
                assert len(arguments) == 5
                assert "table" in arguments
                assert "use_csv_serialization" in arguments
                assert "start_index" in arguments
                assert "end_index" in arguments
                assert "format" in arguments


@pytest.mark.skipif(sys.version_info < (3, 0),
                    reason="In Python 2, we get exception - 'AttributeError: 'module' object has no attribute 'signature''")
def test_without_pandas_class_have_proper_methods(setup_tables_modules):
    _, modules = setup_tables_modules
    without_pandas_table_processing = "_NpTable"
    for module in modules:
        all_classes_methods = __get_classes_and_its_methods_vs_arguments(module)
        if without_pandas_table_processing in all_classes_methods:
            methods_with_arguments = all_classes_methods[without_pandas_table_processing]

            # all methods
            assert "head" in methods_with_arguments
            assert "to_html" in methods_with_arguments
            assert "to_csv" in methods_with_arguments

            # head method
            head_args = methods_with_arguments["head"]
            assert len(head_args) == 2
            assert "self" in head_args
            assert "num_rows" in head_args

            # to_html method
            to_html_args = methods_with_arguments["to_html"]
            assert len(to_html_args) == 2
            assert "self" in to_html_args
            assert "notebook" in to_html_args

            # to_csv method
            to_csv_args = methods_with_arguments["to_csv"]
            assert len(to_csv_args) == 4
            assert "self" in to_csv_args
            assert "na_rep" in to_csv_args
            assert "float_format" in to_csv_args
            assert "sep" in to_csv_args



def test_proper_file_naming(setup_tables_modules):
    """
    Check files' naming.
    :param setup_tables_modules: fixture/data for the test
    """
    files, _ = setup_tables_modules

    for file in files:
        assert file.startswith('pydevd')


def __get_classes_and_its_methods_vs_arguments(module):
    # Get all members of the module
    module_members = inspect.getmembers(module, predicate=inspect.isclass)
    classes_methods = {}

    for cur_class_name, cls in module_members:
        # Ensure the class is defined in the provided module
        if cls.__module__ == module.__name__:
            methods = inspect.getmembers(cls, predicate=inspect.isfunction)
            for method_name, method in methods:
                if cur_class_name not in classes_methods:
                    classes_methods[cur_class_name] = {}
                method_parameters = inspect.signature(method).parameters
                classes_methods[cur_class_name][method_name] = [p for p in method_parameters]

    return classes_methods


def __get_functions_and_its_arguments(module):
    # Get all members of the module
    module_functions = inspect.getmembers(module, predicate=inspect.isfunction)
    functions_to_arguments = {}

    for cur_function_name, cur_function in module_functions:
        if not cur_function_name.startswith('__'):
            functions_to_arguments[cur_function_name] = [p for p in inspect.signature(cur_function).parameters]

    return functions_to_arguments
