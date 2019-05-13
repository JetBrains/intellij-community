
class A:
    <warning descr="Old-style class contains __slots__ definition">__s<caret>lots__</warning> = ""

    def <warning descr="Old-style class contains __getattribute__ definition">__getattribute__</warning>(self, item):
        pass

