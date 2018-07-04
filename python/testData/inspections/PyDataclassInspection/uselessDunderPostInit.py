import dataclasses

@dataclasses.dataclass(init=False)
class A1:
    x: int = 0

    def <warning descr="'__post_init__' would not be called until 'init' parameter is set to True">__post_init__</warning>(self):
        pass