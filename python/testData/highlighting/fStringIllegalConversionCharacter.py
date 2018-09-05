f'{42!r}'
f'{42!s}'
f'{42!a}'
f'{42<error descr="Illegal conversion character 'z': should be one of 's', 'r', 'a'">!z</error>}'
f'{42<error descr="Illegal conversion character 'foo': should be one of 's', 'r', 'a'">!foo</error>}'
f'{42<error descr="Conversion character is expected: should be one of 's', 'r', 'a'">!</error>}'
f'{42<error descr="Conversion character is expected: should be one of 's', 'r', 'a'">!</error>:2}'
f'{42<error descr="Conversion character is expected: should be one of 's', 'r', 'a'">!</error><error descr="} expected">'</error>
f'{42<error descr="Conversion character is expected: should be one of 's', 'r', 'a'">!</error><EOLError descr=": or } expected"></EOLError><EOLError descr="' expected"></EOLError>
