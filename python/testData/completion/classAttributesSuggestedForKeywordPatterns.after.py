class C:
    class_attr = 42

match C():
    case C(class_attr=<caret>):
        pass
