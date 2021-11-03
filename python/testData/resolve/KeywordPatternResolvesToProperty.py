class Class:
    @property
    def foo(self):
        return 42


match Class():
    case Class(foo=42):
#                <ref>
        pass
    