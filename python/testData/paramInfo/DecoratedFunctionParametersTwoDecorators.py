import functools


def decorator1(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        print("using_decorator1")
        return func(*args, **kwargs)
    return wrapper


def decorator2(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        print("using_decorator2")
        return func(*args, **kwargs)
    return wrapper


@decorator1
@decorator2
def function(input_a: int, input_b: float):
    return input_a + input_b


if __name__ == '__main__':
    function(<arg1>)