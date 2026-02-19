class C:
    class_attr = 42

match C():
    case C(cla<caret>):
        pass
