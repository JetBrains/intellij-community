class C:
    attr = 42


match C():
    case C(<caret>):
        pass
