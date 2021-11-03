match 42:
    case <error descr="Pattern makes remaining alternatives unreachable">x</error> | <error descr="Pattern does not bind name x"><warning descr="This code is unreachable">42</warning></error>:
        pass
