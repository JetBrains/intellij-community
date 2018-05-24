class C:
    def _get(self):
        """Getter."""
        return 42

    def _set(self, x):
        pass

    attr = property(fget=_get, fset=_set)


C().at<the_ref>tr = 42