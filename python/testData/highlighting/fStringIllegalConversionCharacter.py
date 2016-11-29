f'{42!r}'
f'{42!s}'
f'{42!a}'
f'{42<error descr="Illegal conversion character 'z': should be one of 's', 'r', 'a'">!z</error>}'
f'{42<error descr="Illegal conversion character 'f': should be one of 's', 'r', 'a'">!f</error>oo}'
f'{42<error descr="Conversion character is expected: should be one of 's', 'r', 'a'">!</error>}'
f'{42<error descr="Conversion character is expected: should be one of 's', 'r', 'a'">!</error>:2}'
f'<error descr="'}' is expected">{42!</error>'
<error descr="Missing closing quote [']">f'<error descr="'}' is expected">{42!</error></error>
