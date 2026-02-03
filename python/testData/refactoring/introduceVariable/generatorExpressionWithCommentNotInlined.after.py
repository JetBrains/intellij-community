baz = [1, 2]
a = (
    el  # comment
    if el >= 0
    else -el
    for el in baz
)
foo = bar(*a)