from override.importsForTypeAnnotations3_import import Foo, Param, Return


class Bar(Foo):
    def func(self, arg: Param) -> Return:
        return super().func(arg)
