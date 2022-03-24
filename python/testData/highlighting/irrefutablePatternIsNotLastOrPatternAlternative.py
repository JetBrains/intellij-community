match x:
    case <error descr="Pattern makes remaining case clauses unreachable">([x] | x)</error>:
        pass
    case <error descr="Pattern makes remaining case clauses unreachable">(<error descr="Pattern makes remaining alternatives unreachable">x</error> | [x])</error>:
        pass
    case <error descr="Pattern makes remaining case clauses unreachable">(<error descr="Pattern makes remaining alternatives unreachable">([x] | x)</error> | [x])</error>:
        pass
    case (<error descr="Pattern makes remaining alternatives unreachable">(<error descr="Pattern makes remaining alternatives unreachable">x</error> | [x])</error> | [x]):
        pass
