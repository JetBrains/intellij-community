from module import symbol as alias

CONST = 42


# x is visible externally in Python 2
[x for x in range(3)]

for i in range(3):
    pass

if True:
    class C:
        class Inner:
            pass

        def method(self):
            pass


def outer_func():
    def inner_func():
        pass
