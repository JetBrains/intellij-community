def process_data_with_type_hint(name: int):
    """
    Text

    Parameters
    ----------
    name : str
        Name parameter
    """
    pass


def process_data_without_type_hint(name):
    """
    Text

    Parameters
    ----------
    name : str
        Name parameter
    """
    pass

process_data_with_type_hint(name=10)
process_data_without_type_hint(<warning descr="Expected type 'str', got 'int' instead">name=10</warning>)
