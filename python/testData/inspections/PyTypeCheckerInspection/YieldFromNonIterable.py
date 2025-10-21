class CustomIterable:
    def __iter__(self):
        return self

    def __next__(self):
        return 42


class CustomIterable2(CustomIterable):
    pass


class NonIterable:
    pass


def g():
    yield from CustomIterable()
    yield from CustomIterable2()
    yield from <warning descr="Expected type 'collections.Iterable', got 'NonIterable' instead">NonIterable()</warning>
    yield from [42]
    yield from "hello"
    yield from <warning descr="Expected type 'collections.Iterable', got 'object' instead">object()</warning>