import main

def target_func():
    main.nothing(None)


def nothing(x):
    pass


def foo(x=bar()):
    ((target_func))()


def bar():
    main.nothing((target_func))


def another():
    ((((target_func), 1))(), 2)()