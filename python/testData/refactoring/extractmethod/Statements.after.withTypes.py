def f():
    a = 1
    b = 1
    foo(a, b)


def foo(a_new: int, b_new: int):
    puts(a_new + b_new * 123)
    print("Hello from extract method")

