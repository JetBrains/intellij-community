from lib1 import urlopen


def g():
    return None


def f(url):
    '''Return the representation available at the URL.

    '''
    return urlopen(url).read()
