class C:
    attr1 = 42


match C():
    case C(attr1=42):
        pass
