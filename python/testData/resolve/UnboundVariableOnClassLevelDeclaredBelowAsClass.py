foo = 'global'

def method(foo):
    class A:
        print(foo)
        #      <ref>
        class foo:
            pass
        print(foo)