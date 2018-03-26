from typing import Protocol, Type


class Proto(Protocol):
    def proto(self, i: int) -> None:
        pass


class Concrete1:
    def proto(self, i: int) -> None:
        pass


class Concrete2(Proto):
    def proto(self, i: int) -> None:
        pass


class Concrete3:
    def proto(self, i: str) -> None:
        pass


class Concrete4(Proto):
    def proto(self, i: str) -> None:
        pass


class NewProto(Proto, Protocol):
    def new_proto(self, s: str) -> None:
        pass


def foo(cls: Type[Proto]) -> None:
    pass


def bar(*classes: Type[Proto]) -> None:
    pass


foo(<warning descr="Only concrete class can be used where 'Type[Proto]' protocol is expected">Proto</warning>)
foo(Concrete1)
foo(Concrete2)
foo(<warning descr="Expected type 'Type[Proto]', got 'Type[Concrete3]' instead">Concrete3</warning>)
foo(Concrete4)  # matched as inheritor
foo(<warning descr="Only concrete class can be used where 'Type[Proto]' protocol is expected">NewProto</warning>)


bar(<warning descr="Only concrete class can be used where 'Type[Proto]' protocol is expected">Proto</warning>)
bar(Concrete1)
bar(Concrete2)
bar(<warning descr="Expected type 'Type[Proto]', got 'Type[Concrete3]' instead">Concrete3</warning>)
bar(Concrete4)  # matched as inheritor
bar(<warning descr="Only concrete class can be used where 'Type[Proto]' protocol is expected">NewProto</warning>)
