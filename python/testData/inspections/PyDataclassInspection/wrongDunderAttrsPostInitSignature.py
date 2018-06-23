import attr

@attr.dataclass
class A:
    a: int

    def __attrs_post_init__(self):
        pass

@attr.dataclass
class B:
    a: int

    def __attrs_post_init__<error descr="'__attrs_post_init__' should not take any parameters except 'self'">(self, a)</error>:
        pass