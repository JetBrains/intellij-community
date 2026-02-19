match x:
    case {:
        pass
    case {"foo"}:
        pass
    case {"foo",}:
        pass
    case {"foo":}:
        pass
    case {"foo":, {"bar": 1}:
        pass
    case {"foo": 1, "baz": ,}
        pass
