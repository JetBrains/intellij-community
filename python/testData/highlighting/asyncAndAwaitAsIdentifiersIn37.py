class <error descr="Identifier expected">async</error>:
    pass


def <error descr="Identifier expected">async</error>():
    pass


async<error descr="'def' or 'with' or 'for' expected"> </error>=<error descr="Statement expected, found Py:EQ"> </error>10


class <error descr="Identifier expected">await</error>:
    pass


def <error descr="Identifier expected">await</error>():
    pass


<error descr="'await' outside async function"><error descr="Cannot assign to await expression">await</error></error><error descr="Expression expected"> </error>= 10