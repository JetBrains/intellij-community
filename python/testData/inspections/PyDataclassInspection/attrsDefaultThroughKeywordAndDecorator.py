import attr


@attr.s
class A1:
    x = attr.ib(default=attr.Factory(int))

    @x.default
    def <error descr="A default is set using 'attr.ib()'">__init_x__</error>(self):
        return 1


@attr.s
class A2:
    x = attr.ib(default=10)

    @x.default
    def <error descr="A default is set using 'attr.ib()'">__init_x__</error>(self):
        return 1


@attr.s
class B1:
    x = attr.ib()

    @x.default
    def __init_x__(self):
        return 1