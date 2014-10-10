baz = [1, 2]
foo = bar(*<selection>(
    el  # comment
    if el >= 0
    else -el
    for el in baz
)</selection>)