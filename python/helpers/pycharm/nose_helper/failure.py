import unittest

from nose_helper.raise_compat import reraise

class Failure(unittest.TestCase):
    """Unloadable or unexecutable test.
    """
    __test__ = False # do not collect
    def __init__(self, exc_class, exc_val, tb = None):
        self.exc_class = exc_class
        self.exc_val = exc_val
        unittest.TestCase.__init__(self)
        self.tb = tb
    def __str__(self):
        return "Failure: %s (%s)" % (
            getattr(self.exc_class, '__name__', self.exc_class), self.exc_val)

    def runTest(self):
        if self.tb is not None:
          reraise(self.exc_class, self.exc_val, self.tb)
        else:
            raise self.exc_class(self.exc_val)