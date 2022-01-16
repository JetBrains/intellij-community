class Base:
    def __init__(self, foo):
        self.foo = foo
        

class Class(Base):
    pass


match Class(1):
    case Class(foo=42):
#                <ref>
        pass
    