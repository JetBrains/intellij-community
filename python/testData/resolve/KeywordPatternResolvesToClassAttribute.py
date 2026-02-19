class Class:
    foo = 42


match Class():
    case Class(foo=42):
#                <ref>
        pass
    