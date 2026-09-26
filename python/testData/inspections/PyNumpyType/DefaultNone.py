def bar(self, baz):
    """
    Text

    Parameters
    ----------
    baz : str, default None
        A parameter with default None. Should not trigger type error.
    """
    pass


def bar_colon(self, baz):
    """
    Parameters
    ----------
    baz : str, default:None
    """
    pass


def bar_colon_space(self, baz):
    """
    Parameters
    ----------
    baz : str, default: None
    """
    pass


def bar_equals(self, baz):
    """
    Parameters
    ----------
    baz : str, default=None
    """
    pass


def bar_equals_spaces(self, baz):
    """
    Parameters
    ----------
    baz : str, default = None
    """
    pass


bar(None, None)
bar(None, "test")
bar(None, baz=None)
bar(None, baz="test")

bar_colon(None, None)
bar_colon(None, baz=None)
bar_colon(None, baz="test")

bar_colon_space(None, None)
bar_colon_space(None, baz=None)
bar_colon_space(None, baz="test")

bar_equals(None, None)
bar_equals(None, baz=None)
bar_equals(None, baz="test")

bar_equals_spaces(None, None)
bar_equals_spaces(None, baz=None)
bar_equals_spaces(None, baz="test")