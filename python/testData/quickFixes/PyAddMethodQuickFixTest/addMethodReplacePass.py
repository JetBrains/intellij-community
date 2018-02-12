class A:
    pass

a = A()
a.<caret><warning descr="Unresolved attribute reference 'y' for class 'A'">y</warning>()
