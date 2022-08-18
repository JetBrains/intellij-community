with <warning descr="Python versions 2.7, 3.5, 3.6, 3.7, 3.8 do not support parenthesized context expressions">(</warning>
    foo() as baz,
    foo() as bar
<warning descr="Python versions 2.7, 3.5, 3.6, 3.7, 3.8 do not support parenthesized context expressions">)</warning>:
    pass

with (foo()) as baz:
    pass

with (foo()):
    pass

with <warning descr="Python versions 2.7, 3.5, 3.6, 3.7, 3.8 do not support parenthesized context expressions">(</warning>foo(),<warning descr="Python versions 2.7, 3.5, 3.6, 3.7, 3.8 do not support parenthesized context expressions">)</warning>:
    pass

with <warning descr="Python versions 2.7, 3.5, 3.6, 3.7, 3.8 do not support parenthesized context expressions">(</warning>foo(), bar()<warning descr="Python versions 2.7, 3.5, 3.6, 3.7, 3.8 do not support parenthesized context expressions">)</warning>:
    pass
