def bar(**kwa<caret>rgs):
    a = kwargs['foo']
    b = kwargs.get('foo', 0)
    c = kwargs.get('foo', 1)