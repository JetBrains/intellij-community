class Baz:
    def long_unique_identifier(self): pass


def xyzzy(baz):
    baz.long_unique_identifier(1, 2, 3)    # don't perform validation for implicit resolve results
