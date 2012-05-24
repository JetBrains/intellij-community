def str_to_none(b):
    """
    :type b: str
    """
    pass


def unicode_to_none(s):
    """
    :type s: unicode
    """
    pass


def string_to_none(s):
    """
    :type s: string
    """
    pass


def str_or_unicode_to_none(s):
    """
    :type s: str or unicode
    """
    pass


def test():
    b1 = 'hello'
    s1 = u'привет'
    b2 = str(-1)
    s2 = unicode(3.14)
    ENC = 'utf-8'
    str_to_none(<warning descr="Expected type 'str', got 'unicode' instead">b1.decode(ENC)</warning>)
    unicode_to_none(b1.decode(ENC))
    string_to_none(b1.decode(ENC))
    str_or_unicode_to_none(b1.decode(ENC))
    b1.encode(ENC)
    s1.decode(ENC)
    str_to_none(s1.encode(ENC))
    unicode_to_none(<warning descr="Expected type 'unicode', got 'str' instead">s1.encode(ENC)</warning>)
    string_to_none(s1.encode(ENC))
    str_or_unicode_to_none(s1.encode(ENC))
    b2.decode(ENC)
    b2.encode(ENC)
    s2.decode(ENC)
    s2.encode(ENC)
