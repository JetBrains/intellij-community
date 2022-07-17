class C:
    @property
    def prop(self):
        pass
        

match C():
    case C(pro<caret>):
        pass