foo = 'global'

def method(foo):
    class A:
        print(foo)
        #      <ref>
        from m1 import bar as foo
        print(foo)