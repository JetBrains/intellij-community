bad_key = []

d1 = {<error descr="Cannot use unhashable type 'list' as a dict key">bad_key</error>: 0}
d2 = {<error descr="Cannot use unhashable type 'list' as a dict key">bad_key</error>: 0 for i in range(5)}

d2.get(<error descr="Cannot use unhashable type 'list' as a dict key">bad_key</error>, [])
d2.pop(<error descr="Cannot use unhashable type 'list' as a dict key">bad_key</error>)
d2.setdefault(<error descr="Cannot use unhashable type 'list' as a dict key">bad_key</error>, [])

d2[<error descr="Cannot use unhashable type 'list' as a dict key">bad_key</error>] = 0
d2[<error descr="Cannot use unhashable type 'list' as a dict key">bad_key</error>], d2[<error descr="Cannot use unhashable type 'set' as a dict key">set()</error>] = 0, 0
