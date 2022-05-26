from typing_extensions import TypedDict, Required, NotRequired


class _MovieBase0(TypedDict):
    title: str


class Movie0(_MovieBase0, total=False):
    year: int


class Movie1(TypedDict):
    title: Required[str]
    year: NotRequired[int]


class Movie2(TypedDict):
    title: NotRequired[str]
    year: NotRequired[int]


def f(movie: Movie0):
    ...


f(Movie1(title="Jaws"))
f(<warning descr="Expected type 'Movie0', got 'Movie2' instead">Movie2(title="Jaws")</warning>)
