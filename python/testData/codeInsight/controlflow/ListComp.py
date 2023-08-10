[(x, y) for k, v in params.items()
        if k
        for x, y in v
        if x > y]
