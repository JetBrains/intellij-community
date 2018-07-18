import attr


@attr.s
class A:
    x = attr.ib()

    @x.default
    def init_x1(self):
        return 10

    @x.validator
    def check_x1(self, attribute, value):
        pass


@attr.s
class A:
    x = attr.ib()

    @x.default
    def init_x2<error descr="'init_x2' should take only 1 parameter">(self, attribute, value)</error>:
        return 10

    @x.validator
    def check_x2<error descr="'check_x2' should take only 3 parameters">(self)</error>:
        pass