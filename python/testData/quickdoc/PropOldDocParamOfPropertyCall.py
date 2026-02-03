class C:
    def _get(self):
        """Getter."""
        return 42

    def _set(self, x):
        """Setter."""
        pass

    attr = property(fget=_get, fset=_set, doc="Docstring")


C().at<the_ref>tr = 42