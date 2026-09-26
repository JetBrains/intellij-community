class SingleElementIterable:
    def __iter__(self):
        return iter([1])

for x in SingleElementIterable():
    # <ref>
    pass
