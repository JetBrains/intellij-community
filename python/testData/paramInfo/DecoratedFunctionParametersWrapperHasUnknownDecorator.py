import functools


def unknown_decorator(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        print("using_decorator")
        return func(*args, **kwargs)
    return wrapper


def decorator(func):
    @unknown_decorator
    def wrapper(*args, **kwargs):
        print("using_decorator")
        return func(*args, **kwargs)
    return wrapper


@decorator
def function(input_a: int, input_b: float):
    return input_a + input_b


if __name__ == '__main__':
    function(<arg1>)