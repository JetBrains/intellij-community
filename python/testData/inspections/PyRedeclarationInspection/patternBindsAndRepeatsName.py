x = 42
match 42:
    case 1 as <warning descr="Redeclared 'x' defined above without usage">x</warning>, 2 as <error descr="Name 'x' is already bound">x</error>, 3 as <error descr="Name 'x' is already bound">x</error>:
        pass
