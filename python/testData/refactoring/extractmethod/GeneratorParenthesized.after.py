def bar():
    return (i + 1 for i in range(1,
                                 10))


_ = bar()