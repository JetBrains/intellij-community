# see https://peps.python.org/pep-0695/#type-parameter-scopes
@dec(list[ T ])  # Runtime error: 'T' is not defined
           <ref>
def func3[T](): ...