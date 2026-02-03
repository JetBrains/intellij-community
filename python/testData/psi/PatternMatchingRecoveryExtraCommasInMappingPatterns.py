match x:
    case {,}:
        pass
    case {'foo': 1, ,}:
        pass
    case {'foo': 1, , 'baz': 3}:
        pass
    case {, , 'baz': 3}:
        pass