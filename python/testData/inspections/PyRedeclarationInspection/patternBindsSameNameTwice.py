match x:
    case [1 as y, <warning descr="Redeclared 'y' defined above without usage">y</warning>]:
        pass
