from warnings import deprecated


class Spam:
    @deprecated("There is enough spam in the world")
    def __add__(self, other: object) -> object:
        ...


spam = Spam()
spam <warning descr="There is enough spam in the world">+=</warning> 1
