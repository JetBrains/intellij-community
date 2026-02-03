class Boo:
    def bu(self, y):
        return 1 + y


class Foo:
    def fu(self):
        return Boo()

x = 0
print(x)
x = Foo().fu()\
.bu(x)
print(x)
x=2
print(x)