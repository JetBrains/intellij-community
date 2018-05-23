class C(object):
    @property
    def attr(self):
        """Docstring."""
        return 42


C().at<the_ref>tr = 42
