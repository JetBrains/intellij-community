import attr


@attr.s
class Foo:
    x = attr.ib()
    y = attr.ib()
    z = attr.ib()

    @x.validator
    def validate_x(self, attribute):
        pass

    @y.validator
    def validate_y(self, attribute, value):
        pass

    def validate_z1(self, ):
        pass

    def validate_z2():
        pass

    @z.default
    def init_z(self, ):
        return 1