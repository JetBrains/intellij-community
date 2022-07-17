match 42:
    case <error descr="Pattern makes remaining case clauses unreachable">x</error>:
        pass
    case <warning descr="This code is unreachable">42</warning>:
        pass