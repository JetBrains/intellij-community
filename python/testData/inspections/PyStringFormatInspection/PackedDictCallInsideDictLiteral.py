"{b}".format(**{"a": 1, **dict(b=2)})
<warning descr="Key 'c' has no corresponding argument">"{c}"</warning>.format(**{"a": 1, **dict(b=2)})