class C:
    def __matmul__(self, other):
        return self


x = C()
x @ y
# <ref>
