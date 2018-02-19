def func():
    ((var, _), _) = ('foo', 1), 2  # type: (([str], [int]), [int])
    var
