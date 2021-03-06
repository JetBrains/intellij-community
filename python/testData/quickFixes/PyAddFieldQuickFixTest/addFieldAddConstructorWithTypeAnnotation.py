class BaseClassAnnotated:
    def __init__(self, name: str):
        self.name = name


class DerivedClass(BaseClassAnnotated):
    def get_param(self):
        return self.<caret><warning descr="Unresolved attribute reference 'param' for class 'DerivedClass'">param</warning>
