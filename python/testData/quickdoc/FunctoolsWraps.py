import functools

class Cls:
    def foo(self, s: str, b: bool):
        """
        Doc text
        :param s: str
        :param b: bool
        :return: None
        """
        pass

class Route:
    @functools.wraps(Cls.foo)
    def __init__(self):
        pass

class Router:
    @functools.wraps(wrapped=Route.__init__)
    def route(self, s: str):
        pass

r = Router()
r.<the_ref>route(13)