def foo(i):
    return i


def generate_power(exponent):
    def decorator(f):
        def inner(*args):
            result = f(*args)
            return exponent ** result

        return inner

    return decorator


@generate_power(foo(foo(3)))  # breakpoint
@generate_power(foo(foo(5)))
def raise_three(n):
    return n


@generate_power(2)
def raise_two(n):
    return n


raise_three(raise_two(2))  # breakpoint
