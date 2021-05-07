match 42:
    case x:
        pass
    case <warning descr="This code is unreachable">42</warning>:
        pass