from typing_extensions import Protocol


class A:

    @property
    def <warning descr="Getter should return or yield something">normal_property</warning>(self):
        pass


class B(Protocol):
    @property
    def protocol_property(self):
        pass
