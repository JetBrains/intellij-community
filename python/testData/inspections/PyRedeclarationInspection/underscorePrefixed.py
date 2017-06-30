def foo(_a):
  _a = 10


l = lambda _a: [_a for _a in []]


f2e = [(f, e) for _a, e, f in [(1, 2, 3)]]
e2f = [(e, f) for <warning descr="Redeclared '_a' defined above without usage">_a</warning>, e, f in [(1, 2, 3)]]

array = [1, 2, 3]
<warning descr="Redeclared '_a' defined above without usage">_a</warning>, mid, <warning descr="Redeclared '_a' defined above without usage">_a</warning> = array