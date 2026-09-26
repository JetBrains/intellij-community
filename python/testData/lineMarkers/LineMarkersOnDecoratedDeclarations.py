def decorator(x):
    return x


@decorator
class MyClass:
    pass


@decorator
def func():
    pass
