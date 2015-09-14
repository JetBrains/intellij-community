t = (1, <warning descr="Python versions < 3.5 do not support starred expressions in tuples, lists, and sets">*(2, 3)</warning>)
a, <warning descr="Python versions < 3.0 do not support starred expressions as assignment targets">*b</warning>, c = t
