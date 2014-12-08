from b import Generic


class C(Generic['foo']):
    pass


print(C['bar'])
c = C()
print(c<warning descr="Class 'C' does not define '__getitem__', so the '[]' operator cannot be used on its instances">[</warning>'baz'])
