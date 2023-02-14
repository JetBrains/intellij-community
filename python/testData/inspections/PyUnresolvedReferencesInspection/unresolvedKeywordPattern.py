class Class:
    def __init__(self, foo):
        self.foo = foo


match Class(1, 2):
    case Class(<error descr="Unresolved reference 'baz'">baz</error>=42):
        pass
    