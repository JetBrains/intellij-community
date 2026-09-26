class C:
    def __init__(self):
        self.__attr = 42


match C():
    case C(__<caret>):
        pass        
