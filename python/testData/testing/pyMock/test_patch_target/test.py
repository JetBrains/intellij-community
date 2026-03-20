#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from unittest.mock import patch, DEFAULT

# Valid: patching an attribute
@patch("example_module.MyClass")
def test_valid_class(mock_cls):
    pass

@patch("example_module.top_level_function")
def test_valid_function(mock_func):
    pass

# Valid: patch.multiple() takes a module as its target
@patch.multiple("example_module", top_level_function=DEFAULT)
def test_valid_patch_multiple(**mocks):
    pass

# Invalid: patching a module
@patch(<warning descr="Cannot patch module 'example_module'. Target must be an attribute (e.g., 'example_module.ClassName' or 'example_module.function_name')">"example_module"</warning>)
def test_invalid_module(mock_mod):
    pass

# With context manager
with patch(<warning descr="Cannot patch module 'example_module'. Target must be an attribute (e.g., 'example_module.ClassName' or 'example_module.function_name')">"example_module"</warning>):
    pass


# Invalid: a target without a dot that is not a module
@patch(<warning descr="Cannot patch 'missing_name'. Target must be a dotted path to an attribute, such as 'module.missing_name'">"missing_name"</warning>)
def test_invalid_no_dot(mock_obj):
    pass

# Valid: the value of an f-string is not known
name = "example_module.MyClass"
with patch(f"{name}"):
    pass