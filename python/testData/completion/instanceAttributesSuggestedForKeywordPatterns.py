class C:
    def __init__(self):
        self.instance_attr = 42 

match C():
    case C(ins<caret>):
        pass
