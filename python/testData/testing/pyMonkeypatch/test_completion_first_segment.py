import pytest


def test_setattr_complete_first(monkeypatch):
    monkeypatch.setattr("<caret>", None)
