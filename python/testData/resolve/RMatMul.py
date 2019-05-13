class C:
    def __rmatmul__(self, other):
        return self


x = C()
y @ x
# <ref>
