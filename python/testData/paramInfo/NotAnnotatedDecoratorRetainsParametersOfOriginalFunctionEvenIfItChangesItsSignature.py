def decorator(func):
    def wrapper(extra: str, *args, **kwargs):
        return func(*args, **kwargs)
    return wrapper


@decorator
def function(input_a: int, input_b: float):
    return input_a + input_b


if __name__ == '__main__':
    function(<arg1>)