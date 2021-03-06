import attr

@attr.s
class A:
  a = attr.ib(kw_only=True)
  b = attr.ib(type=int)

A(<arg1>)


@attr.s
class B1:
  a = attr.ib(kw_only=True)

@attr.s
class D1(B1):
  b = attr.ib(type=int)

D1(<arg2>)


@attr.s
class B2:
  a = attr.ib(type=int)

@attr.s
class D2(B2):
  b = attr.ib(kw_only=True)

D2(<arg3>)


@attr.s
class B3:
  a = attr.ib(type=int)

@attr.s
class D3(B3):
  a = attr.ib(kw_only=True)

D3(<arg4>)


@attr.s
class B4:
  a = attr.ib(kw_only=True)

@attr.s
class D4(B4):
  a = attr.ib(type=int)

D4(<arg5>)

