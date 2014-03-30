class URLOpener(object):
    def __init__(self, x):
        self.x = x

    def urlopen(self):
        return file(self.x)