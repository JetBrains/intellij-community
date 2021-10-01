match 42:
    case x | <warning descr="This code is unreachable">42</warning>:
        pass
