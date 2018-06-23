foo = 'global'

def method(foo):
    class A:
        print(foo)
        #      <ref>
        def foo(self):
            pass
        print(foo)