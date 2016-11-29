def f():
    return dict(foo=0)


'{foo}'.format(**f())