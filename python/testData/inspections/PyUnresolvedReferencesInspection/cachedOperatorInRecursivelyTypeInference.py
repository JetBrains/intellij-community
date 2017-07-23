def test_if_list(value):
    if isinstance(value, list):
        v = [test_if_list(x) for x in value]
        if len(v) > 1:
            return v
        else:
            return v[0]

    return value