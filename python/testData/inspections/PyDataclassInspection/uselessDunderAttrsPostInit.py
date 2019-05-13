import attr

@attr.dataclass(init=False)
class A1:
    x: int = 0

    def <warning descr="'__attrs_post_init__' would not be called until 'init' parameter is set to True">__attrs_post_init__</warning>(self):
        pass