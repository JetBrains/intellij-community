import pytest


def test_setattr_through_package(monkeypatch):
    monkeypatch.setattr("example_package.submodule.SubClass.sub_method", lambda self: None)
