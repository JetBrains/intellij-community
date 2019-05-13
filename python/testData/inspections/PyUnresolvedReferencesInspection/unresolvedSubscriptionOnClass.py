class Foo(object):
    def __getitem__(self, item):
        return item

Foo<warning descr="Class 'type' does not define '__getitem__', so the '[]' operator cannot be used on its instances">[</warning>0]
