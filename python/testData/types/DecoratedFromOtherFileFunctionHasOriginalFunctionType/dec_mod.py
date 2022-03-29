import functools


def decorator(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        print("using_decorator1")
        return func(*args, **kwargs)
    return wrapper