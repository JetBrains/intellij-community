Tuple = _TupleType(tuple, -1, inst=False, name='Tuple')
Tuple.__doc__ = \
    """Deprecated alias to builtins.tuple.

    Tuple[X, Y] is the cross-product type of X and Y.

    Example: Tuple[T1, T2] is a tuple of two elements corresponding
    to type variables T1 and T2.  Tuple[int, float, str] is a tuple
    of an int, a float and a string.

    To specify a variable-length tuple of homogeneous type, use Tuple[T, ...].
    """