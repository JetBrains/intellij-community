def foo(w, <caret>q = 2, **kwargs):
    doSomething(kwargs.get('foo', 22))