from typing import TypedDict


class Movie (TypedDict):
    name: str
    age: int


class Foo:
    def foo(self, movie: Movie) -> None:
        pass


Foo().foo(((({<caret>}))))
