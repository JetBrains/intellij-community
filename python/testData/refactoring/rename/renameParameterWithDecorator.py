from functools import wraps


def d(x):
    def dec(f):
        @wraps(f)
        def wrapper(*args, **kwargs):
            print(f'x = {x}')
            return f(*args, **kwargs)
        return wrapper
    return dec


class C:
    foo = 0

    @d(foo)  # 2. This is renamed either but it shouldn't
    def f(self, <caret>foo):  # 1. Rename this 'foo' to 'bar'
        print(foo)


C().f(1)