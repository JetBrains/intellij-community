class Box2[X]:
    def m(self) -> X: # inferred as 'out Any'
        pass
    @classmethod
    def f(cls) -> X<the_ref>: # inferred as 'Any'
        pass