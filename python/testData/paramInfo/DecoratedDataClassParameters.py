from dataclasses import dataclass


def new_init(self, bar):
    self.bar = bar


def dec(cls):
    cls.__init__ = new_init
    return cls


@dec
@dataclass
class Foo:
    bar: int


Foo(<arg1>)