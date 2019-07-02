from src import foo


res = foo(1, 2)


def bar():
    from src import foo
    res1 = fo<caret>o(1, 2)