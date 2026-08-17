"""
Tests variance of TypeVarTuple.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#variance-inference


from typing import Generic
from typing_extensions import TypeVarTuple

class InvariantTypeVarTuple[*InOutTs]:
    a: tuple[*InOutTs]

in_out_obj: InvariantTypeVarTuple[object] = InvariantTypeVarTuple[int]()  # E
in_out_int: InvariantTypeVarTuple[int] = InvariantTypeVarTuple[object]()  # E
in_out_int = InvariantTypeVarTuple[int]()
in_out_variadic_int: InvariantTypeVarTuple[*tuple[int, ...]] = InvariantTypeVarTuple[*tuple[object, ...]]()  # E
in_out_variadic_object: InvariantTypeVarTuple[*tuple[object, ...]] = InvariantTypeVarTuple[*tuple[int, ...]]()  # E
in_out_empty: InvariantTypeVarTuple[()] = InvariantTypeVarTuple[()]()  # OK
in_out_fixed_from_variadic: InvariantTypeVarTuple[int] = InvariantTypeVarTuple[*tuple[int, ...]]()  # E
in_out_variadic_from_fixed: InvariantTypeVarTuple[*tuple[int, ...]] = InvariantTypeVarTuple[int]()  # E


class ContravariantTypeVarTuple[*InTs]:
    def f(self, t: tuple[*InTs]):
        raise NotImplementedError

in_obj: ContravariantTypeVarTuple[object, object] = ContravariantTypeVarTuple[object, int]()  # E
in_int: ContravariantTypeVarTuple[int] = ContravariantTypeVarTuple[object]()  # OK
in_variadic_int: ContravariantTypeVarTuple[*tuple[int, ...]] = ContravariantTypeVarTuple[*tuple[object, ...]]()  # OK
in_variadic_object: ContravariantTypeVarTuple[*tuple[object, ...]] = ContravariantTypeVarTuple[*tuple[int, ...]]()  # E
in_empty: ContravariantTypeVarTuple[()] = ContravariantTypeVarTuple[()]()  # OK
in_fixed_from_variadic: ContravariantTypeVarTuple[int] = ContravariantTypeVarTuple[*tuple[int, ...]]()  # OK
in_variadic_from_fixed: ContravariantTypeVarTuple[*tuple[int, ...]] = ContravariantTypeVarTuple[int]()  # E


class CovariantTypeVarTuple[*OutTs]:
    def f(self) -> tuple[*OutTs]:
        raise NotImplementedError


out_int: CovariantTypeVarTuple[int] = CovariantTypeVarTuple[object]()  # E
out_obj: CovariantTypeVarTuple[object] = CovariantTypeVarTuple[int]()  # OK
out_multiple1: CovariantTypeVarTuple[int, int] = CovariantTypeVarTuple[bool,  bool]()  # OK
out_multiple2: CovariantTypeVarTuple[int, int] = CovariantTypeVarTuple[bool,  object]()  # E
out_multiple3: CovariantTypeVarTuple[int, int] = CovariantTypeVarTuple[object,  bool]()  # E
out_multiple4: CovariantTypeVarTuple[int, int] = CovariantTypeVarTuple[object,  object]()  # E
out_variadic_int: CovariantTypeVarTuple[*tuple[int, ...]] = CovariantTypeVarTuple[*tuple[object, ...]]()  # E
out_variadic_object: CovariantTypeVarTuple[*tuple[object, ...]] = CovariantTypeVarTuple[*tuple[int, ...]]()  # OK
out_empty: CovariantTypeVarTuple[()] = CovariantTypeVarTuple[()]()  # OK
out_fixed_from_variadic: CovariantTypeVarTuple[int] = CovariantTypeVarTuple[*tuple[int, ...]]()  # E
out_variadic_from_fixed: CovariantTypeVarTuple[*tuple[int, ...]] = CovariantTypeVarTuple[int]()  # OK

Ts = TypeVarTuple("Ts")  # OK
InferTs = TypeVarTuple("InferTs", infer_variance=True)  # OK
InvTs1 = TypeVarTuple("InvTs1", covariant=True, contravariant=True)  # E
InvTs2 = TypeVarTuple("InvTs2", covariant=True, infer_variance=True)  # E
InvTs3 = TypeVarTuple("InvTs3", contravariant=True, infer_variance=True)  # E

class InvariantTypeVarTupleOld(Generic[*Ts]):
    def in_f(self, *args: *Ts) -> None:  # OK
        raise NotImplementedError

    def out_f(self) -> tuple[*Ts]:  # OK
        raise NotImplementedError


obj_old: InvariantTypeVarTupleOld[object] = InvariantTypeVarTupleOld[int]()  # E
int_old: InvariantTypeVarTupleOld[int] = InvariantTypeVarTupleOld[object]()  # E
int_old = InvariantTypeVarTupleOld[int]()


InTs = TypeVarTuple("InTs", contravariant=True)

class ContravariantTypeVarTupleOld(Generic[*InTs]):
    def in_f(self, *args: *InTs) -> None:  # OK
        raise NotImplementedError

    def out_f(self) -> tuple[*InTs]:  # E
        raise NotImplementedError


in_obj_old: ContravariantTypeVarTupleOld[object] = ContravariantTypeVarTupleOld[int]()  # E
in_int_old: ContravariantTypeVarTupleOld[int] = ContravariantTypeVarTupleOld[object]()  # OK


OutTs = TypeVarTuple("OutTs", covariant=True)

class CovariantTypeVarTupleOld(Generic[*OutTs]):
    def in_f(self, *args: *OutTs) -> None:  # E
        raise NotImplementedError

    def out_f(self) -> tuple[*OutTs]:  # OK
        raise NotImplementedError


out_int_old: CovariantTypeVarTupleOld[int] = CovariantTypeVarTupleOld[object]()  # E
out_obj_old: CovariantTypeVarTupleOld[object] = CovariantTypeVarTupleOld[int]()  # OK


# `infer_variance=True` on a traditional `TypeVarTuple`
class InferredContravariantTypeVarTupleOld(Generic[*InferTs]):
    def in_f(self, *args: *InferTs) -> None:  # OK
        raise NotImplementedError


infer_in_obj_old: InferredContravariantTypeVarTupleOld[object] = InferredContravariantTypeVarTupleOld[int]()  # E
infer_in_int_old: InferredContravariantTypeVarTupleOld[int] = InferredContravariantTypeVarTupleOld[object]()  # OK


class InferredCovariantTypeVarTupleOld(Generic[*InferTs]):
    def out_f(self) -> tuple[*InferTs]:  # OK
        raise NotImplementedError


infer_out_int_old: InferredCovariantTypeVarTupleOld[int] = InferredCovariantTypeVarTupleOld[object]()  # E
infer_out_obj_old: InferredCovariantTypeVarTupleOld[object] = InferredCovariantTypeVarTupleOld[int]()  # OK
