from functools import wraps
import inspect


class Route:
    def __init__(self, input_a: int, input_b: float):
        ...


class Router:
    def __init__(self):
        self.routes = []

    @wraps(Route.__init__)
    def route(self, *args, **kwargs):
        route = Route(*args, **kwargs)
        self.routes.append(route)


r = Router()
r.route(<arg1>)
