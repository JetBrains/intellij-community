#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

class GenericMeta(type):
    def __getitem__(self, args):
        pass


class Generic(object):
    __metaclass__ = GenericMeta


class C(Generic['int']):
    pass


print(C['bar'])
c = C()
print(c<warning descr="Class 'C' does not define '__getitem__', so the '[]' operator cannot be used on its instances">[</warning>'baz'])
