class Base:
    def __init__(self):
        self.inherited_attr = 42


class C(Base):
    pass


match C():
    case C(inh<caret>):
        pass
