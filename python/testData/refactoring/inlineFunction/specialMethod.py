class Additive:
    def __init__(self, value):
        self.value = value

    def __add__(self, other):
        return Additive(self.value + other.value)


Additive(1) +<caret> Additive(1)