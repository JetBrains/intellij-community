from required import A, AlternativeSyntax


a: A = <warning descr="TypedDict 'A' has missing keys: 'x', 'y'">{}</warning>
a1: A = {'x': 42, 'y': 42}
a2: AlternativeSyntax = {'y': <warning descr="Expected type 'int', got 'str' instead">"str"</warning>}
