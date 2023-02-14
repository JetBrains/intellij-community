import attr


@attr.s
class C:
    y = attr.ib()


c = C(y=3)
print(c.y)
