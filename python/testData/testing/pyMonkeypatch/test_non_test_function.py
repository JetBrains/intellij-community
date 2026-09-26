import example_module
from example_module import MyClass


# Not a test function — monkeypatch parameter should NOT get references
def bar(monkeypatch):
    monkeypatch.setattr("example_module.MyClass", lambda: None)
    monkeypatch.setattr(MyClass, "my_method", lambda self: None)


# Test function — monkeypatch parameter SHOULD get references
def test_foo(monkeypatch):
    monkeypatch.setattr("example_module.MyClass", lambda: None)
    monkeypatch.setattr(MyClass, "my_method", lambda self: None)
