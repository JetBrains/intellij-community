abc = 3
bcd = 5

if abc * bcd > 3:
    print(abc)
else:
    print(bcd)


def function(aaa, bbb):
    if aaa - bbb > aaa:
        print(aaa)
    else:
        print(bbb)

function(322, function(abc, bcd))

class Clazz:
    field = 3

    def function(self, aaa, bbb):
        if aaa - bbb > aaa:
            print(aaa)
        else:
            print(bbb - self.field)

    def __init__(self) -> None:
        self.function(2, self.function(2, 3 + self.field))
        print(self.field)


classVar = Clazz()
classVar.function(1, 2)

def string_function(sss, trrrr):
    string = '322' + "1" + '4444'
    print('eee' + 'rrr' + "33" + '22')
    return string + '32222' + '1111' + "1"


class AnotherClass:
    field = "field"

    def self_returning_function(self, arg):
        return self

    def func(self, arg):
        return arg


def function_for_replace_completion():
    instance = AnotherClass()
    ss = instance.self_returning_function(322).self_returning_function(instance.func(0)) \
        .self_returning_function(1).field
