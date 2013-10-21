from lib1 import URLOpener
import lib1


class C(object):
    def __init__(self):
        self.opener = lib1.URLOpener(None)

    def f(self, x):
        o = URLOpener(x)
        return o.urlopen()

    def g(self, x):
        return 'f({0!r}) = {1!r}'.format(URLOpener(x), lib1.URLOpener(x))

def main():
    c = C()
    print c