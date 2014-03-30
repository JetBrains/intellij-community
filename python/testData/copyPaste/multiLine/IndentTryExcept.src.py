class A:
    def foo(self, dct, key):
        try:
<selection>            return dct[key]
        except KeyError:
            pass
</selection>
