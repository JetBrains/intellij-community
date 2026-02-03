# see https://peps.python.org/pep-0695/#type-parameter-scopes
class ClassA[T](BaseClass[T], param = Foo[T]): ...  # OK

print(T)  # Runtime error: 'T' is not defined
      <ref>