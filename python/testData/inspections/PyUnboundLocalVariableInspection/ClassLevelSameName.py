local_var = "foo"

class C:
    local_var = local_var #pass

    def foo(self):
        print(self.local_var)

C().foo()
