from annotated import A


a: A = <warning descr="Expected type 'int', got 'LiteralString' instead">'str'</warning>
a1: A = 42
