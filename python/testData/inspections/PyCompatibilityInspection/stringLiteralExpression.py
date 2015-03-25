a = <warning descr="Python version 3.0, 3.1, 3.2 do not support a 'U' prefix">u</warning>"String"

# python 3.3

a = <warning descr="Python version 3.0, 3.1, 3.2 do not support a 'U' prefix">u</warning>""
a = r""
a = <warning descr="Python version 2.4, 2.5 do not support a 'B' prefix">b</warning>""
a = <error descr="Python version 2.7 does not support a 'RB' prefix"><warning descr="Python version 2.4, 2.5, 2.6, 2.7, 3.0, 3.1, 3.2 do not support a 'RB' prefix">rb</warning></error>""
a = <warning descr="Python version 2.4, 2.5, 3.0 do not support a 'BR' prefix">br</warning>""

# python 3.2, 3.1

a = r""
a = <warning descr="Python version 2.4, 2.5 do not support a 'B' prefix">b</warning>""
a = <warning descr="Python version 2.4, 2.5, 3.0 do not support a 'BR' prefix">br</warning>""

# python 3.0

a = r""
a = <warning descr="Python version 2.4, 2.5 do not support a 'B' prefix">b</warning>""

# python 2.7, 2.6

a = <warning descr="Python version 3.0, 3.1, 3.2 do not support a 'U' prefix">u</warning>""
a = r""
a = <warning descr="Python version 3.0, 3.1, 3.2, 3.3, 3.4, 3.5 do not support a 'UR' prefix">ur</warning>""
a = <warning descr="Python version 2.4, 2.5 do not support a 'B' prefix">b</warning>""
a = <warning descr="Python version 2.4, 2.5, 3.0 do not support a 'BR' prefix">br</warning>""

# python 2.5

a = <warning descr="Python version 3.0, 3.1, 3.2 do not support a 'U' prefix">u</warning>""
a = r""
a = <warning descr="Python version 3.0, 3.1, 3.2, 3.3, 3.4, 3.5 do not support a 'UR' prefix">ur</warning>""

# combined
b = <warning descr="Python version 3.0, 3.1, 3.2 do not support a 'U' prefix">u</warning>"" <warning descr="Python version 2.4, 2.5 do not support a 'B' prefix">b</warning>""

# never was available
a = <error descr="Python version 2.7 does not support a 'RR' prefix"><warning descr="Python version 2.4, 2.5, 2.6, 2.7, 3.0, 3.1, 3.2, 3.3, 3.4, 3.5 do not support a 'RR' prefix">rr</warning></error>""
a = <error descr="Python version 2.7 does not support a 'BB' prefix"><warning descr="Python version 2.4, 2.5, 2.6, 2.7, 3.0, 3.1, 3.2, 3.3, 3.4, 3.5 do not support a 'BB' prefix">bb</warning></error>""
a = <error descr="Python version 2.7 does not support a 'UU' prefix"><warning descr="Python version 2.4, 2.5, 2.6, 2.7, 3.0, 3.1, 3.2, 3.3, 3.4, 3.5 do not support a 'UU' prefix">uu</warning></error>""