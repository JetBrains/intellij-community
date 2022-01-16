class C:
    def __init__(self):
        self.attr = 42


match C():
    case C(attr = <caret>):
        pass