def main(indices):
    foo = True
    for i in indices:
        <selection>need_break = False
        if i > 2:
            foo = False
            need_break = True</selection>
        if need_break:
            break
    return foo