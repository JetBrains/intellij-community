import b
<warning descr="'a' is 'Final' and could not be reassigned">b.a</warning> = 2

from b import a
<warning descr="'a' is 'Final' and could not be reassigned">a</warning> = 3