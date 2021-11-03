class Class:
    def __init__(self, foo):
        self.foo = foo


match Class(1, 2):
    case Class(f<caret>oo=42):
        pass
    