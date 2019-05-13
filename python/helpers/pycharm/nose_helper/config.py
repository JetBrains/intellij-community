import os
import re

class Config(object):
    """nose configuration.
    """

    def __init__(self, **kw):
        self.env = kw.pop('env', {})
        self.testMatchPat = r'(?:^|[\b_\.%s-])[Tt]est' % os.sep
        self.testMatch = re.compile(self.testMatchPat)
        self.srcDirs = ('lib', 'src')
        self.workingDir = os.getcwd()
        self.update(kw)

    def __repr__(self):
        dict = self.__dict__.copy()
        dict['env'] = {}
        keys = [ k for k in dict.keys()
                 if not k.startswith('_') ]
        keys.sort()
        return "Config(%s)" % ', '.join([ '%s=%r' % (k, dict[k])
                                          for k in keys ])
    __str__ = __repr__

    def update(self, d):
        self.__dict__.update(d)
