from .importsForTypeAnnotations1_import import Foo


class Bar(Foo):
    def func(self, arg: int) -> int:
        return super().func(arg)
