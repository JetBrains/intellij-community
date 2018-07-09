import dataclasses


@dataclasses.dataclass(<error descr="'frozen' should be false if the class defines '__setattr__' or '__delattr__'">frozen=True</error>)
class A4:
    a: int = 1

    def __setattr__(self, key, value):
        pass


class Base4:
    def __setattr__(self, key, value):
        pass


@dataclasses.dataclass(frozen=True)
class Derived4(Base4):
    d: int = 1


@dataclasses.dataclass(<error descr="'frozen' should be false if the class defines '__setattr__' or '__delattr__'">frozen=True</error>)
class A2:
    a: int = 1

    def __delattr__(self, key):
        pass


class Base2:
    def __delattr__(self, key):
        pass


@dataclasses.dataclass(frozen=True)
class Derived2(Base2):
    d: int = 1