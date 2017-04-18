from typing import List, Union, Sequence, Optional, Dict


class Class:
    module = ...  # type: str
    name = ...  # type: str
    super = ...  # type: Optional[List[Union["Class", str]]]
    methods = ...  # type: Dict[str, int]
    file = ...  # type: int
    lineno = ...  # type: int

    def __init__(self,
                 module: str,
                 name: str,
                 super: Optional[List[Union["Class", str]]],
                 file: str,
                 lineno: int) -> None: ...


class Function:
    module = ...  # type: str
    name = ...  # type: str
    file = ...  # type: int
    lineno = ...  # type: int

    def __init__(self,
                 module: str,
                 name: str,
                 file: str,
                 lineno: int) -> None: ...


def readmodule(module: str,
               path: Optional[Sequence[str]] = ...
               ) -> Dict[str, Class]: ...


def readmodule_ex(module: str,
                  path: Optional[Sequence[str]] = ...
                  ) -> Dict[str, Union[Class, Function, List[str]]]: ...
