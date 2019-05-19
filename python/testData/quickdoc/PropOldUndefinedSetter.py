class C(object):
    def _get(self):
        """Docstring."""
        return 42

    attr = property(fget=_get)

C().at<the_ref>tr = 42
