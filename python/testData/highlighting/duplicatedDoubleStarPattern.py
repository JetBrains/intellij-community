match 42:
    case {**xs, <error descr="Repeated star pattern">**ys</error>, <error descr="Repeated star pattern">**zs</error>}:
        pass
