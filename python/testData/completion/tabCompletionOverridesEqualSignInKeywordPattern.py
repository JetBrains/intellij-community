class C:
    attr1 = 42


match C():
    case C(att<caret>r2=42):
        pass
