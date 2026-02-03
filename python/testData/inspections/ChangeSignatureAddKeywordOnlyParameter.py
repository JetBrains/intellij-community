def func(x, *args, foo=None):
    pass

func(<caret>1, 2, 3, <warning descr="Unexpected argument">bar='spam'</warning>)