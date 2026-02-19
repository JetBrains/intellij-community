match 42:
    case <error descr="Pattern makes remaining alternatives unreachable">x</error> | <error descr="Pattern does not bind name x">42</error>:
        pass
