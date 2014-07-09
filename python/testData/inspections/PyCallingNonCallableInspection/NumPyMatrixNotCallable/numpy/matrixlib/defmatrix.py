_all__ = ['matrix']

import numpy.core.numeric as N

class matrix(N.ndarray):
    """
    matrix(data, dtype=None, copy=True)

    Returns a matrix from an array-like object, or from a string of data.
    A matrix is a specialized 2-D array that retains its 2-D nature
    through operations.  It has certain special operators, such as ``*``
    (matrix multiplication) and ``**`` (matrix power).

    Parameters
    ----------
    data : array_like or string
       If `data` is a string, it is interpreted as a matrix with commas
       or spaces separating columns, and semicolons separating rows.
    dtype : data-type
       Data-type of the output matrix.
    copy : bool
       If `data` is already an `ndarray`, then this flag determines
       whether the data is copied (the default), or whether a view is
       constructed.

    See Also
    --------
    array

    Examples
    --------
    >>> a = np.matrix('1 2; 3 4')
    >>> print a
    [[1 2]
     [3 4]]

    >>> np.matrix([[1, 2], [3, 4]])
    matrix([[1, 2],
            [3, 4]])

    """
    pass
