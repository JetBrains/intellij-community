class Foo:
    """
    Attributes
    ----------
    bar
        Something cool
    """

    def __init__(self):
        self.bar = 1


class Baz(Foo):
    """
    Attributes
    ----------
    bar
    <ref>
        Re-documented but does exist still.
    """