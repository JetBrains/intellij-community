from _typeshed import Incomplete

from pyVmomi.vmodl import ManagedObject

def __getattr__(name: str) -> Incomplete: ...

class InvalidArgument(Exception): ...

class ManagedObjectNotFound(Exception):
    obj: ManagedObject
