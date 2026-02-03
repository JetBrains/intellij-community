match 42:
    case <error descr="Single star pattern cannot be used outside sequence patterns">*xs</error>:
        pass
    case (<error descr="Single star pattern cannot be used outside sequence patterns">*xs</error>):
        pass
    case (*xs,):
        pass
    case [*xs]:
        pass
    case {'foo': bar, <error descr="Single star pattern cannot be used outside sequence patterns">*xs</error><error descr="':' expected">}</error>:
        pass
    case str(<error descr="Single star pattern cannot be used outside sequence patterns">*xs</error>):
        pass
    case <error descr="Single star pattern cannot be used outside sequence patterns">*xs</error> as alias:
        pass
    case [xs] | <error descr="Single star pattern cannot be used outside sequence patterns">*xs</error>:
        pass

        