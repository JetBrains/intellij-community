from typing import ClassVar, Final

from docutils.transforms import Transform

__docformat__: Final = "reStructuredText"

class Filter(Transform):
    default_priority: ClassVar[int]
    def apply(self) -> None: ...
