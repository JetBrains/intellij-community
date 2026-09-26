class A(type):
    @classmethod
    def __prepare__(metacls, name: str, bases: tuple[type, ...], /, **kwds: Any) -> MutableMapping[str, object]:


class B(type):
    @classmethod
    def __prepare__(metacls, name: str, bases: tuple[type, ...], /, **kwds: Any) -> MutableMapping[str, object]:

class C(type):
    @classmethod
    @decorator
    def __prepare__(metacls, name: str, bases: tuple[type, ...], /, **kwds: Any) -> MutableMapping[str, object]:

class D:
    def __prep

# so the added imports don't offset the carets
from typing import List, Any, MutableMapping
