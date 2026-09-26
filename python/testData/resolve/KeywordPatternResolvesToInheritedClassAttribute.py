class Base:
    foo = 42


class Class(Base):
    pass    


match Class():
    case Class(foo=42):
#                <ref>
        pass
    