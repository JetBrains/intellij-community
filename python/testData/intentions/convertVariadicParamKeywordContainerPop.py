def foo(w, <caret>q = 2, **kwargs):
    a = kwargs.pop('tmp')
    doSomething(kwargs.pop('foo', 22))
    doSomething(kwargs.pop('bar', default=23))