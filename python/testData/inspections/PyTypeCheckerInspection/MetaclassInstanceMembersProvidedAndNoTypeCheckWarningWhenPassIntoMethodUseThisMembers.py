def expecting(p):
    print(p.meta_inst_lvl)
    print(p.meta_cls_lvl)


class MyMeta(type):
    meta_cls_lvl = 10

    def __init__(cls, what, bases, dict):
        super().__init__(what, bases, dict)
        cls.meta_inst_lvl = 20


class MyClass(metaclass=MyMeta):
    pass


expecting(MyClass)  # check that there is no warnings
