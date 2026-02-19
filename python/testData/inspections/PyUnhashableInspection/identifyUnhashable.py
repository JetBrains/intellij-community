class HashableClass:
    def __hash__(self):
        pass


class HashableClass2:
    pass  # works too


class UnhashableClass:
    def __eq__(self, other):
        pass  # Python considers unhashable classes with __eq__ but without __hash__


class UnhashableClass2:
    __hash__ = None


d = {}

d[<error descr="Cannot use unhashable type 'list' as a dict key">[1, 2]</error>] = 0
d[<error descr="Cannot use unhashable type 'set' as a dict key">{1, 2}</error>] = 0
d[<error descr="Cannot use unhashable type 'UnhashableClass' as a dict key">UnhashableClass()</error>] = 0
d[<error descr="Cannot use unhashable type 'UnhashableClass2' as a dict key">UnhashableClass2()</error>] = 0
d[HashableClass()] = 0
d[HashableClass2()] = 0
d[5] = 0
d[frozenset([1, 2])] = 0
d[object()] = 0

d[(1, (2, 3))] = 0
d[<error descr="Cannot use unhashable type '(int, (int, list))' as a dict key">(1, (2, []))</error>] = 0

unhashable_union: int | list = 5
d[<error descr="Cannot use unhashable type 'int | list' as a dict key">unhashable_union</error>] = 0

hashable_union: int | str = 5
d[hashable_union] = 0
