import functools


def decorator(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        print("using_decorator")
        return func(42, 42.0)
    return wrapper


@decorator
def function(input_a: int, input_b: float):
    return input_a + input_b


if __name__ == '__main__':
    function(<arg1>)