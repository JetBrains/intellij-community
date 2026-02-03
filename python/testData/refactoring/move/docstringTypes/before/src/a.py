class C(object):
    def __eq__(self, other):
        '''
        :type self: :class:`C`
        :type other: :py:class:`a.C`
        '''
        return super(self, C).__eq__(other)


def f(x, y):
    '''Returns the first argument.

    :type x: (int, :py:class:`C`, C)
    :param C y: ignored
    :rtype: :class:`a.C`

    '''
    _, c = x
    return c
