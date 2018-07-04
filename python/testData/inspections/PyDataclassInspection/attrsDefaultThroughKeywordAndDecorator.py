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


@attr.s
class C1:
    a = attr.ib()

    @a.default
    def init_a_1(self):
        return 1

    @a.default
    def <error descr="A default is set using 'init_a_1'">init_a_2</error>(self):
        return 2


@attr.s
class D1:
    a = attr.ib(factory=list)

    @a.default
    def <error descr="A default is set using 'attr.ib()'">init_a</error>(self):
        return 1