match x:
    case {}:
        pass
    case {"foo": 1}:
        pass
    case {"foo": 1,}:
        pass
    case {"foo": {"bar": []}}:
        pass
    case {"foo": 1, "bar": 2}:
        pass
    case {"foo": 1, **args}:
        pass