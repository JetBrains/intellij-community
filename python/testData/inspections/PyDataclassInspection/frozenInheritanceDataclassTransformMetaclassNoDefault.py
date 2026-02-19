from typing import dataclass_transform

@dataclass_transform()  # No frozen_default
class ModelMeta(type):
    pass

class ModelBase(metaclass=ModelMeta):
    pass

# OK: frozen is not specified, so it's undefined
class Customer1(ModelBase):
    id: int
    name: str

# OK: can explicitly set frozen=True
class Customer2(ModelBase, frozen=True):
    id: int
    name: str

# OK: can explicitly set frozen=False
class Customer3(ModelBase, frozen=False):
    id: int
    name: str

# OK: Customer2 has frozen=True, Customer4 doesn't specify frozen (undefined)
# This is OK because Customer4 doesn't have an explicit frozen state that conflicts
class Customer4(Customer2):
    salary: float

# OK: Both have explicit frozen=True
class Customer5(Customer2, frozen=True):
    salary: float

# OK: Customer3 has frozen=False, subclass doesn't specify (undefined)
class Customer6(Customer3):
    salary: float

# OK: Both have frozen=False
class Customer7(Customer3, frozen=False):
    salary: float

# This should generate an error: trying to make frozen=False when parent is frozen=True
class Customer8(Customer2, <error descr="Frozen dataclasses can not inherit non-frozen one and vice versa">frozen=False</error>):  # E
    salary: float

# OK: Can mutate Customer3 because it's explicitly non-frozen
c3 = Customer3(id=1, name="test")
c3.id = 2

# This should generate an error: Customer2 is frozen
c2 = Customer2(id=1, name="test")
<error descr="'Customer2' object attribute 'id' is read-only">c2.id</error> = 2  # E
