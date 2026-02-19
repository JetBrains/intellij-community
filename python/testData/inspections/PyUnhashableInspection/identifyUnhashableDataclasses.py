from dataclasses import dataclass, field




@dataclass
class DefaultUnhashable:
    x: int

hash(<error descr="Cannot hash 'DefaultUnhashable'">DefaultUnhashable(5)</error>)




@dataclass(frozen=True)
class FrozenHashable:
    x: int

hash(FrozenHashable(5))




@dataclass(unsafe_hash=True)
class UnsafelyHashable:
    x: int

hash(UnsafelyHashable(5))




@dataclass(eq=False)
class EqFalseHashable:
    x: int

hash(EqFalseHashable(5))




class UnhashableBase:
    __hash__ = None

@dataclass(eq=False)
class SubUnhashable(UnhashableBase):
    x: int

hash(<error descr="Cannot hash 'SubUnhashable'">SubUnhashable(5)</error>)




@dataclass
class ExplicitlyHashable:
    x: int

    def __hash__(self):
        return hash(self.x)

hash(ExplicitlyHashable(5))




@dataclass(frozen=True)
class ExplicitlyUnhashable:
    x: int
    __hash__ = None

hash(<error descr="Cannot hash 'ExplicitlyUnhashable'">ExplicitlyUnhashable(5)</error>)
