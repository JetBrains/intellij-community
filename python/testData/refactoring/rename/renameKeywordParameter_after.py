class Class:
    def __init__(self, foo):
        self.bar = foo


match Class(1, 2):
    case Class(bar=42):
        pass
    