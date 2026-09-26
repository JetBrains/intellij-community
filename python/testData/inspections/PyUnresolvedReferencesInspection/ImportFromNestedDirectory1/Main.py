import deriv

a1 = deriv.Simple()
a1.<warning descr="Unresolved attribute reference 'xxx' for class 'Simple'">xxx</warning>()

b2 = deriv.DerivedBaseInit()
b2.<warning descr="Unresolved attribute reference 'yyy' for class 'DerivedBaseInit'">yyy</warning>()
