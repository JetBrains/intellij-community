import b
<warning descr="'a' is 'Final' and cannot be reassigned">b.a</warning> = 2

from b import a
<warning descr="'a' is 'Final' and cannot be reassigned">a</warning> = 3