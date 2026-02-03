class Mapping:
    def iterkeys(self):
        return self

class MappingImpl(dict, Mapping):
    iterkeys = Mapping.iterkeys

MappingImpl().iterkeys()
