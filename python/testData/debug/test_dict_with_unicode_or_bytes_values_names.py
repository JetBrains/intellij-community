val = u'\u201cFoo\u201d'
name = u"u'Foo \u201cFoo\u201d Bar' (4706573888)"
dict_with_unicode = {name: val}
print(dict_with_unicode)

val = b"\x00\x10"
name = b"\xfc\x00"
dict_with_bytes = {name: val}
print(dict_with_bytes)
