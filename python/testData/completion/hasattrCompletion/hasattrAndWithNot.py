def foo(x):
    if hasattr(x, 'foo') and not hasattr(x, 'bar') and not not hasattr(x, 'baz'):
        print(x.<caret>)