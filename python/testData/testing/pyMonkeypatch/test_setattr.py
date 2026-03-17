import pytest

import example_module
from example_module import MyClass


# --- Dotted string form: monkeypatch.setattr("module.Class.attr", value) ---

def test_setattr_dotted_class(monkeypatch):
    monkeypatch.setattr("example_module.MyClass", lambda: None)


def test_setattr_dotted_method(monkeypatch):
    monkeypatch.setattr("example_module.MyClass.my_method", lambda self: None)


def test_setattr_dotted_function(monkeypatch):
    monkeypatch.setattr("example_module.top_level_function", lambda: None)


def test_setattr_dotted_unresolved(monkeypatch):
    monkeypatch.setattr("example_module.DoesNotExist", lambda: None)


# --- Object + attribute form: monkeypatch.setattr(obj, "attr", value) ---

def test_setattr_object_method(monkeypatch):
    monkeypatch.setattr(MyClass, "my_method", lambda self: None)


def test_setattr_object_attr(monkeypatch):
    monkeypatch.setattr(MyClass, "class_attr", "new_value")


def test_setattr_object_unresolved(monkeypatch):
    monkeypatch.setattr(MyClass, "does_not_exist", "value")


def test_setattr_module_function(monkeypatch):
    monkeypatch.setattr(example_module, "top_level_function", lambda: None)


# --- delattr ---

def test_delattr_dotted(monkeypatch):
    monkeypatch.delattr("example_module.MyClass.class_attr")


def test_delattr_object(monkeypatch):
    monkeypatch.delattr(MyClass, "class_attr")
