def f(*args, **kwargs):
    # type: (*str, **str) -> None
    args.index('foo')
    kwargs.pop('bar')
