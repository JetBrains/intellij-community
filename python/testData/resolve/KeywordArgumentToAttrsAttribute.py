import attr


@attr.s
class C:
    some_attr = attr.ib()


C(some_attr=3)
#   <ref>
