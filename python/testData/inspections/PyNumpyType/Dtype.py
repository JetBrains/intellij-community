def maximum_fill_value(obj):
    """
    Return the minimum value that can be represented by the dtype of an object.

    This function is useful for calculating a fill value suitable for
    taking the maximum of an array with a given dtype.

    Parameters
    ----------
    obj : dtype
        An object that can be queried for it's numeric type.

    Returns
    -------
    val : scalar
        The minimum representable value.

    Raises
    ------
    TypeError
        If `obj` isn't a suitable numeric type.

    """
    errmsg = "Unsuitable type for calculating maximum."
    if hasattr(obj, 'dtype'):
        return _recursive_extremum_fill_value(obj.dtype, max_filler)
    elif isinstance(obj, float):
        return max_filler[ntypes.typeDict['float_']]
    elif isinstance(obj, int):
        return max_filler[ntypes.typeDict['int_']]
    elif isinstance(obj, long):
        return max_filler[ntypes.typeDict['uint']]
    elif isinstance(obj, np.dtype):
        return max_filler[obj]
    else:
        raise TypeError(errmsg)

a = np.int8()
maximum_fill_value(a)


maximum_fill_value(('i4',[('r','u1'), ('g','u1'), ('b','u1'), ('a','u1')]))
maximum_fill_value("i8,f8,S5")
