class Class:
    def __init__(self, foo):
        self.foo = foo


match Class(1):
    case Class(foo=42):
#                <ref>
        pass
    