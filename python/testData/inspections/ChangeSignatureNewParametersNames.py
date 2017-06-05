def func(i1):
    i2 = 'Spam'


x = 42 or 'str'
func<warning descr="Unexpected argument(s)">(<caret>1, <warning descr="Unexpected argument">2</warning>, <warning descr="Unexpected argument">x</warning>)</warning>