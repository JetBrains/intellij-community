def test_default_true(copy):
    """
    Text

    Parameters
    ----------
    copy : bool, default True
        Whether to copy data
    """
    pass


def test_default_equals_true(copy):
    """
    Text

    Parameters
    ----------
    copy : bool, default=True
        Whether to copy data
    """
    pass


def test_default_colon_true(copy):
    """
    Text

    Parameters
    ----------
    copy : bool, default: True
        Whether to copy data
    """
    pass


def test_default_false(inplace):
    """
    Text

    Parameters
    ----------
    inplace : bool, default False
        Whether to modify in place
    """
    pass


def test_default_integer(count):
    """
    Text

    Parameters
    ----------
    count : int, default 10
        Number of items
    """
    pass


def test_default_string(name):
    """
    Text

    Parameters
    ----------
    name : str, default "test"
        Name parameter
    """
    pass


# Matching the documented type should not trigger warnings
test_default_true(copy=True)
test_default_true(copy=False)

test_default_equals_true(copy=True)

test_default_colon_true(copy=False)

test_default_false(inplace=True)

test_default_integer(count=5)

test_default_string(name="custom")

# A non-None default must not make the parameter Optional
test_default_true(<warning descr="Expected type 'bool', got 'None' instead">copy=None</warning>)
test_default_equals_true(<warning descr="Expected type 'bool', got 'None' instead">copy=None</warning>)
test_default_colon_true(<warning descr="Expected type 'bool', got 'None' instead">copy=None</warning>)
test_default_string(<warning descr="Expected type 'str', got 'None' instead">name=None</warning>)