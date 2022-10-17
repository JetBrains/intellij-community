a <<< a
a <<< a_b
a <<< a b
a <<<a
a <<<a_b
a <<<a b
a <<<'abc'
a <<< 'abc'
a <<<"a"
a <<< "a"
a <<<`abc`
a <<< `abc`
a <<<$(abc)
a <<< $(abc)
$(a <<< [abc])
a <<< {}
a <<< {a}
a <<< []
a <<< [a]
a <<< [$a
a <<< [$a]
a <<< [${a}]
a <<< [$(a)]
a <<< [$((1))]
read <<< x
#comment
read <<< "x"
#comment
read <<< "x"
if
read <<< "x"
a
read <<< x <<< a
read <<< "x" <<< a
mysql <<<'CREATE DATABASE dev' || exit
mysql <<<'CREATE DATABASE dev' && exit
mysql <<<'CREATE DATABASE dev'||exit
mysql <<<'CREATE DATABASE dev'&&exit
mysql <<<'abc'; exit
mysql <<<'abc';exit
grep x <<< 'X' >/dev/null && echo 'Found' || echo 'Not found'
grep x <<<$var >/dev/null && echo 'Found' || echo 'Not found'
a <<< (a)
a <<< " [$a]
