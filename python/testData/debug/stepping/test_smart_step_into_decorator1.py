def deco(func):
    def wrapper(x):
        x = x + 2
        return func(x)
    return wrapper


@deco
def f(x):
    return x


y = f(1) + f(2)  # breakpoint
