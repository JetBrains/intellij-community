class B:
    def foo(self):
        return self.<caret><warning descr="Unresolved attribute reference 'x' for class 'B'">x</warning>
