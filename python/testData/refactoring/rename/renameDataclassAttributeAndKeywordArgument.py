import attr


@attr.s
class C:
    x = attr.ib()


c = C(x<caret>=3)
print(c.x)
