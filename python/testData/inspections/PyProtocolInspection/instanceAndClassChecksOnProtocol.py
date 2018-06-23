from typing import runtime, Protocol


@runtime
class Closable1(Protocol):
    def close(self):
        pass


class Closable2(Protocol):
    def close(self):
        pass


class ClosableImpl:
    def close(self):
        pass


assert isinstance(ClosableImpl(), Closable1)
assert issubclass(ClosableImpl, Closable1)

assert isinstance(ClosableImpl(), <error descr="Only @runtime protocols can be used with instance and class checks">Closable2</error>)
assert issubclass(ClosableImpl, <error descr="Only @runtime protocols can be used with instance and class checks">Closable2</error>)

assert isinstance(ClosableImpl(), <error descr="Only @runtime protocols can be used with instance and class checks">Protocol</error>)
B = Protocol
assert issubclass(ClosableImpl, <error descr="Only @runtime protocols can be used with instance and class checks">B</error>)
