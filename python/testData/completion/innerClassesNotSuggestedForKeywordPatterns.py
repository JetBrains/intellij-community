class C:
    class InnerClass:
        pass
        

match C():
    case C(Inner<caret>):
        pass