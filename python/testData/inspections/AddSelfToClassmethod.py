class A:
    def foo(self):
        pass

    @classmethod
    def pop(cls):
        print <error descr="Unresolved reference 'foo'">fo<caret>o</error>()