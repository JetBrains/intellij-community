bad_key = []

s1 = {<error descr="Cannot use unhashable type 'list' as a set element">bad_key</error>}
s2 = {<error descr="Cannot use unhashable type 'list' as a set element">bad_key</error> for i in range(5)}

s2.add(<error descr="Cannot use unhashable type 'list' as a set element">bad_key</error>)
s2.remove(<error descr="Cannot use unhashable type 'list' as a set element">bad_key</error>)
s2.discard(<error descr="Cannot use unhashable type 'list' as a set element">bad_key</error>)
