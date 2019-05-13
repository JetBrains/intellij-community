class O(object):
    __slots__ = ["a"]

    def __init__(self):
        self.b =  1

    @property
    def b(self):
        return self.a

    @b.setter
    def b(self, value):
        self.a = value


inst = O()
inst.b = 42
print(inst.a)
