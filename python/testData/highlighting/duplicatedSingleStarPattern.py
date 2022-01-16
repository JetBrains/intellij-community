match 42:
    case *xs, <error descr="Repeated star pattern">*ys</error>:
        pass
    case *xs, <error descr="Repeated star pattern">*_</error>:
        pass
    case *xs, <error descr="Repeated star pattern">*ys</error>, <error descr="Repeated star pattern">*zs</error>:
        pass
    case (*xs, <error descr="Repeated star pattern">*ys</error>):
        pass
    case [*xs, <error descr="Repeated star pattern">*ys</error>]:
        pass
