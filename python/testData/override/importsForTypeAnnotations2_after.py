from typing import Union

from .importsForTypeAnnotations2_import import Foo


class Bar(Foo):
    def something(self, arg: Union[dict, int]) -> Union[None, int]:
        return super().something(arg)
