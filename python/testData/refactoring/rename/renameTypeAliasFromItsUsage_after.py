type Renamed[T] = dict[str, T]


def f[T](x: Renamed[T]) -> T:
    ...