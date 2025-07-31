def f():
    a = 1
    b = 1
    foo(a, b)


def foo(a_new: int, b_new: int):
    print(a_new + b_new * 123)