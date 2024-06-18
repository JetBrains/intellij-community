import functools


class MyClass:
    def foo(self, s: str, i: int):
        pass

class Route:
    @functools.wraps(MyClass.foo)
    def __init__(self):
        pass

class Router:
    @functools.wraps(wrapped=Route.__init__)
    def route(self, s: str):
        pass