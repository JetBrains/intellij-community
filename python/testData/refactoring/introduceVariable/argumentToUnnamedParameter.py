from typing import NewType
SomeType = NewType("SomeType", bytes)
SomeType(b"va<caret>lue")