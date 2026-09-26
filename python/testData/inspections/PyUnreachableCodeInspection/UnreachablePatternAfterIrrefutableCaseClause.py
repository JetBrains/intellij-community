match 42:
    case <error descr="Pattern makes remaining case clauses unreachable">x</error>:
        pass
    case 42:
        <warning descr="This code is unreachable">pass</warning>