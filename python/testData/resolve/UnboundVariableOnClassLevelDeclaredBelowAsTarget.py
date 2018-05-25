foo = 'global'

def method(foo):
    class A:
        print(foo)
        #      <ref>
        foo = 'local'
        print(foo)