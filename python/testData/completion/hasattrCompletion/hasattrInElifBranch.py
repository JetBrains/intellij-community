def foo(x: int):
    if hasattr(x, 'foo1'):
        print(x.foo1)
    elif hasattr(x, 'foo2'):
        print(x.<caret>)