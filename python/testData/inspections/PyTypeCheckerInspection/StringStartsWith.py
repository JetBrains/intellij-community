'foo'.startswith('bar')
'foo'.startswith(('bar', 'baz'))
'foo'.startswith(<warning descr="Expected type 'Union[unicode, Tuple[unicode, ...]]', got 'int' instead">2</warning>)

u'foo'.startswith(u'bar')
u'foo'.startswith((u'bar', u'baz'))
u'foo'.startswith(<warning descr="Expected type 'Union[unicode, Tuple[unicode, ...]]', got 'int' instead">2</warning>)
