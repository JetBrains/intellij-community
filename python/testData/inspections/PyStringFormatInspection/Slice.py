def foo(x):
    return x

artist = foo(1)
print('%s' % (artist.lower()[0:10]))

unicode_content = foo(u"test")
assert isinstance(unicode_content, unicode)
print '%s...' % unicode_content[:200]
