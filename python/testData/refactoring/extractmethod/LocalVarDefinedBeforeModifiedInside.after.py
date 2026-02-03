def main(indices):
    foo = True
    for i in indices:
        foo, need_break = bar(foo, i)
        if need_break:
            break
    return foo


def bar(foo_new, i_new):
    need_break = False
    if i_new > 2:
        foo_new = False
        need_break = True
    return foo_new, need_break