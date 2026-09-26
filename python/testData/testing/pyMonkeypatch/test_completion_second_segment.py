import pytest


def test_setattr_complete_second(monkeypatch):
    monkeypatch.setattr("example_module.<caret>", None)
