def foo():
    """
    Parameters:
      <weak_warning descr="Unexpected parameter a in docstring">a</weak_warning>: foo
      <weak_warning descr="Unexpected parameter c in docstring"><caret>c</weak_warning> (int): start of description
        continuation line 1
        continuation line 2

    Returns:
      None
    """