import sys

if sys.version_info >= (3, 0):
    unicode = str
    unichr = chr
    basestring = str
    interactive_prompt = input
    from html.parser import HTMLParser as HTMLParser
    from io import StringIO as StringIO
else:
    unicode = unicode
    unichr = unichr
    basestring = basestring
    interactive_prompt = raw_input
    from StringIO import StringIO as StringIO

    from HTMLParser import HTMLParser as HTMLParser

def coerce_string(value): ...
def is_string(value): ...
def is_unicode(value): ...
def on_macos(): ...
def on_windows(): ...
