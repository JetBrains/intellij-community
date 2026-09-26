# see https://peps.python.org/pep-0695/#type-parameter-scopes
T = 0

def func2[T](a = list[ T ]): ...  # Runtime error: 'T' is not defined
                       <ref>