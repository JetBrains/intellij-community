class BaseClassAnnotated:
    def __init__(self, name: str):
        self.name = name


class DerivedClass(BaseClassAnnotated):
    def __init__(self, name: str):
        super().__init__(name)
        self.param = None

    def get_param(self):
        return self.param
