from typing import Callable


def outer_decorator[**P, T](f: Callable[P, T]) -> Callable[P, T]:
    return f


class NonWorkingClass:
    @outer_decorator
    def add_two(self, x: float, y: float) -> float:
        return x + y
