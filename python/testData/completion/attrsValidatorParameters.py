import attr


@attr.s
class Foo:
    x = attr.ib()
    y = attr.ib()
    z = attr.ib()

    @x.validator
    def validate_x(self, <caret>):
        pass

    @y.validator
    def validate_y(self, attribute, <caret>):
        pass

    def validate_z1(self, <caret>):
        pass

    def validate_z2(<caret>):
        pass

    @z.default
    def init_z(self, <caret>):
        return 1