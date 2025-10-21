import project.deriv

a1 = project.deriv.Simple()
a1.<warning descr="Unresolved attribute reference 'xxx' for class 'Simple'">xxx</warning>()

b2 = project.deriv.DerivedBase()
b2.<warning descr="Unresolved attribute reference 'yyy' for class 'DerivedBase'">yyy</warning>()

c3 = project.deriv.DerivedSub()
c3.<warning descr="Unresolved attribute reference 'zzz' for class 'DerivedSub'">zzz</warning>()
