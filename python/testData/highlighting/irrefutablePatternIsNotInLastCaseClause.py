match 42:
    case <error descr="Pattern makes remaining case clauses unreachable">_</error>:
        pass
    case x if x > 0:
        pass
    case <error descr="Pattern makes remaining case clauses unreachable">x</error>:
        pass
    case 42:
        pass
