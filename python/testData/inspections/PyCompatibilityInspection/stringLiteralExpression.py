a = u"String"

# Python 3.6

a = <error descr="Python version 2.7 does not support a 'F' prefix"><warning descr="Python version 2.6, 3.4, 3.5 do not support a 'F' prefix">f</warning></error>""
a = <error descr="Python version 2.7 does not support a 'F' prefix"><warning descr="Python version 2.6, 3.4, 3.5 do not support a 'F' prefix">F</warning></error>""
a = <error descr="Python version 2.7 does not support a 'RF' prefix"><warning descr="Python version 2.6, 3.4, 3.5 do not support a 'RF' prefix">rf</warning></error>""
a = <error descr="Python version 2.7 does not support a 'FR' prefix"><warning descr="Python version 2.6, 3.4, 3.5 do not support a 'FR' prefix">fr</warning></error>""
a = <error descr="Python version 2.7 does not support a 'FU' prefix"><warning descr="Python version 2.6, 3.4, 3.5, 3.6, 3.7 do not support a 'FU' prefix">fu</warning></error>""
a = <error descr="Python version 2.7 does not support a 'UF' prefix"><warning descr="Python version 2.6, 3.4, 3.5, 3.6, 3.7 do not support a 'UF' prefix">uf</warning></error>""
a = <error descr="Python version 2.7 does not support a 'BF' prefix"><warning descr="Python version 2.6, 3.4, 3.5, 3.6, 3.7 do not support a 'BF' prefix">bf</warning></error>""
a = <error descr="Python version 2.7 does not support a 'FB' prefix"><warning descr="Python version 2.6, 3.4, 3.5, 3.6, 3.7 do not support a 'FB' prefix">fb</warning></error>""
a = <error descr="Python version 2.7 does not support a 'UFR' prefix"><warning descr="Python version 2.6, 3.4, 3.5, 3.6, 3.7 do not support a 'UFR' prefix">ufr</warning></error>""

# python 3.3

a = u""
a = r""
a = b""
a = <error descr="Python version 2.7 does not support a 'RB' prefix"><warning descr="Python version 2.6 does not support a 'RB' prefix">rb</warning></error>""
a = br""

# python 3.2, 3.1

a = r""
a = b""
a = br""

# python 3.0

a = r""
a = b""

# python 2.7, 2.6

a = u""
a = r""
a = <warning descr="Python version 3.4, 3.5, 3.6, 3.7 do not support a 'UR' prefix">ur</warning>""
a = b""
a = br""

# python 2.5

a = u""
a = r""
a = <warning descr="Python version 3.4, 3.5, 3.6, 3.7 do not support a 'UR' prefix">ur</warning>""

# combined, PY-32321
b = <warning descr="Python version 3.4, 3.5, 3.6, 3.7 do not allow to mix bytes and non-bytes literals">u"" b""</warning>
b = <warning descr="Python version 3.4, 3.5, 3.6, 3.7 do not allow to mix bytes and non-bytes literals">r"" b""</warning>
b = <warning descr="Python version 3.4, 3.5, 3.6, 3.7 do not allow to mix bytes and non-bytes literals"><error descr="Python version 2.7 does not support a 'F' prefix"><warning descr="Python version 2.6, 3.4, 3.5 do not support a 'F' prefix">f</warning></error>"" b""</warning>

# never was available
a = <error descr="Python version 2.7 does not support a 'RR' prefix"><warning descr="Python version 2.6, 3.4, 3.5, 3.6, 3.7 do not support a 'RR' prefix">rr</warning></error>""
a = <error descr="Python version 2.7 does not support a 'BB' prefix"><warning descr="Python version 2.6, 3.4, 3.5, 3.6, 3.7 do not support a 'BB' prefix">bb</warning></error>""
a = <error descr="Python version 2.7 does not support a 'UU' prefix"><warning descr="Python version 2.6, 3.4, 3.5, 3.6, 3.7 do not support a 'UU' prefix">uu</warning></error>""