# --- TypeField ---

class TypeFieldMeta(type):
    pass


class TypeField(metaclass=TypeFieldMeta):
    pass


# --- FieldedType ---

class FieldedTypeMeta(type):
    pass


class FieldedType(metaclass=FieldedTypeMeta):
    pass


# --- CompositeField ---

class CompositeFieldMeta(type(FieldedType), type(TypeField)):
    # The superclass declaration is spelled they way it is to true and isolate this bit of code
    # from the implementation details of whether or not FieldedType and TypeField have special
    # metaclasses of their own or not and still get a metaclass MRO that keeps Python happy.
    pass
