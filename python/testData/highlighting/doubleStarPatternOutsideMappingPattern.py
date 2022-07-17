match 42:
    case <error descr="Double star pattern cannot be used outside mapping patterns">**xs</error>:
        pass
    case (<error descr="Double star pattern cannot be used outside mapping patterns">**xs</error>):
        pass
    case (<error descr="Double star pattern cannot be used outside mapping patterns">**xs</error>,):
        pass
    case [<error descr="Double star pattern cannot be used outside mapping patterns">**xs</error>]:
        pass
    case {'foo': bar, **xs}:
        pass
    case str(<error descr="Double star pattern cannot be used outside mapping patterns">**xs</error>):
        pass
    case <error descr="Double star pattern cannot be used outside mapping patterns">**xs</error> as alias:
        pass
    case [xs] | <error descr="Double star pattern cannot be used outside mapping patterns">**xs</error>:
        pass

        