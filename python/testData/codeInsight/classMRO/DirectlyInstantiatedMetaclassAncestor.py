class MetaBase(type):
    pass


class Meta(MetaBase):
    pass


Base = Meta('Base', (), {})


class MyClass(Base):
    pass