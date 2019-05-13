"{b}".format(**{"a": 1, **{"b":2}})
"{c}".format(**{**{"b":2}, "a": 1, **{"c":2}})
<warning descr="Key 'd' has no corresponding argument">"{d}"</warning>.format(**{**{"b":2}, "a": 1, **{"c":2}})