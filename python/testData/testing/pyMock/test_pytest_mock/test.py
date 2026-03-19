#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from example_module import MyClass
from pytest_mock import MockerFixture


def test_mocker_fixture_type(mocker):
    """Test that mocker parameter has MockerFixture type."""
    pass


def test_mocker_patch_call(mocker):
    """Test that mocker.patch() is recognized as a patch call."""
    mocker.patch("example_module.MyClass.my_method")


def test_mocker_patch_object_call(mocker):
    """Test that mocker.patch.object() is recognized."""
    mocker.patch.object(MyClass, "my_method")


class TestMockerFixture:
    def test_mocker_in_class(self, mocker):
        """Test mocker fixture in a test class."""
        mocker.patch("example_module.top_level_function")

    def test_mocker_type_in_method(self, mocker):
        """Test that mocker has correct type in class methods."""
        pass


def test_mocker_with_other_fixtures(mocker, tmp_path):
    """Test mocker alongside other fixtures."""
    mocker.patch("example_module.MyClass")
