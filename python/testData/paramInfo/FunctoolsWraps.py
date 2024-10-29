import functools


class MyClass:
    def foo(self, s: str, b: bool):
        pass


class Route:
    @functools.wraps(MyClass.foo)
    def __init__(self, a: int, b: float, c: object):
        pass


class Router:
    @functools.wraps(wrapped=Route.__init__)
    def route(self, *args, **kwargs):
        pass


r = Router()
r.route(<arg1>"", <arg2>True)
