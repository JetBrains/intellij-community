"""
Tests variance of ParamSpec.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#variance-inference


from typing import Callable, Generic, ParamSpec


class InvariantParamSpec[**InOutP]:
    a: Callable[InOutP, None]

in_out_obj: InvariantParamSpec[object] = InvariantParamSpec[int]()  # E
in_out_int: InvariantParamSpec[int] = InvariantParamSpec[object]()  # E


class ContravariantParamSpec[**InP]:
    def f(self, *args: InP.args, **kwargs: InP.kwargs): ...

in_obj: ContravariantParamSpec[object] = ContravariantParamSpec[int]()  # E
in_int: ContravariantParamSpec[int] = ContravariantParamSpec[object]()  # OK


class CovariantParamSpec[**OutP]:
    def f(self, fn: Callable[OutP, None]) -> None:
        raise NotImplementedError


out_int: CovariantParamSpec[int] = CovariantParamSpec[object]()  # E
out_obj: CovariantParamSpec[object] = CovariantParamSpec[int]()  # OK

# cases involving keyword-only, positional-only parameters, parameter names, defaults and differing callable arities
class Box[T]:
    t: T

    def __init__(self, t: T): ...


def f(a: int): ...
def kw(*, a: int): ...
def pos(a: int, /): ...
def names(b: int): ...
def default(a: int = 1): ...
def arity(a: int, b: str): ...


class InitP[**P]:  # contravariant
    def __init__(self, fn: Callable[P, None]): ...

    def usage(self) -> Callable[P, None]:
        """infer contravariance"""
        raise NotImplementedError


# `InitP` produces a `Callable[P, None]`, so a replacement is only safe if it
# accepts every call form that `InitP[(a: int)]` accepts.
in_box = Box(InitP(f))

in_kw_p = InitP(kw)
in_box.t = in_kw_p  # E
in_pos_p = InitP(pos)
in_box.t = in_pos_p  # E
in_names_p = InitP(names)
in_box.t = in_names_p  # E
in_default_p = InitP(default)
in_box.t = in_default_p  # OK
in_arity_p = InitP(arity)
in_box.t = in_arity_p  # E


class OutitP[**P]:  # covariant
    def __init__(self, fn: Callable[P, None]): ...

    def usage(self, fn: Callable[P, None]):
        """infer covariance"""


# `OutitP` consumes a `Callable[P, None]`, so the safe direction is reversed: a
# replacement is only safe if `Callable[(a: int), None]` can be passed to it.
out_box = Box(OutitP(f))

out_kw_p = OutitP(kw)
out_box.t = out_kw_p  # OK
out_pos_p = OutitP(pos)
out_box.t = out_pos_p  # OK
out_names_p = OutitP(names)
out_box.t = out_names_p  # E
out_default_p = OutitP(default)
out_box.t = out_default_p  # E
out_arity_p = OutitP(arity)
out_box.t = out_arity_p  # E


# old style
P = ParamSpec("P")  # OK
InP = ParamSpec("InP", contravariant=True)  # OK
OutP = ParamSpec("OutP", covariant=True)  # OK
InferP = ParamSpec("InferP", infer_variance=True)  # OK
InvP1 = ParamSpec("InvP1", covariant=True, contravariant=True)  # E
InvP2 = ParamSpec("InvP2", covariant=True, infer_variance=True)  # E
InvP3 = ParamSpec("InvP3", contravariant=True, infer_variance=True)  # E

class InvariantParamSpecOld(Generic[P]):
    def f(self, fn: Callable[P, None]) -> Callable[P, None]:  # OK
        raise NotImplementedError

in_out_old: InvariantParamSpecOld[int]
in_out_old = InvariantParamSpecOld[int]()  # OK
in_out_old = InvariantParamSpecOld[bool]()  # E
in_out_old = InvariantParamSpecOld[object]()  # E

class ContravariantParamSpecOld(Generic[InP]):
    def in_f(self) -> Callable[InP, None]:  # OK
        raise NotImplementedError

    def out_f(self, fn: Callable[InP, None]) -> None:  # E
        raise NotImplementedError


in_obj_old: ContravariantParamSpecOld[object] = ContravariantParamSpecOld[int]()  # E
in_int_old: ContravariantParamSpecOld[int] = ContravariantParamSpecOld[object]()  # OK


class CovariantParamSpecOld(Generic[OutP]):
    def in_f(self) -> Callable[OutP, None]:  # E
        raise NotImplementedError
    def out_f(self, fn: Callable[OutP, None]) -> None:  # OK
        raise NotImplementedError


out_int_old: CovariantParamSpecOld[int] = CovariantParamSpecOld[object]()  # E
out_obj_old: CovariantParamSpecOld[object] = CovariantParamSpecOld[int]()  # OK


# `infer_variance=True` on a traditional `ParamSpec`
class InferredContravariantParamSpecOld(Generic[InferP]):
    def in_f(self) -> Callable[InferP, None]:  # OK
        raise NotImplementedError


infer_in_obj_old: InferredContravariantParamSpecOld[object] = InferredContravariantParamSpecOld[int]()  # E
infer_in_int_old: InferredContravariantParamSpecOld[int] = InferredContravariantParamSpecOld[object]()  # OK


class InferredCovariantParamSpecOld(Generic[InferP]):
    def out_f(self, fn: Callable[InferP, None]) -> None:  # OK
        raise NotImplementedError


infer_out_int_old: InferredCovariantParamSpecOld[int] = InferredCovariantParamSpecOld[object]()  # E
infer_out_obj_old: InferredCovariantParamSpecOld[object] = InferredCovariantParamSpecOld[int]()  # OK
