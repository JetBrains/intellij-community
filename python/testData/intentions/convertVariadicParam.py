def foo(w, <caret>q = 2, **kwargs):
    a = kwargs['tmp']
    doSomething(kwargs.get('foo', 22))