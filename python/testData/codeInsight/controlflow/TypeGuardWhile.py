from typing import List
from typing_extensions import TypeGuard
import foo

def checkit(foo: List[int]) -> TypeGuard[List[str]]:
    pass

x = foo.bar()
while checkit(x):
    x = foo.bar()