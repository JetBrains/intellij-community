# see https://peps.python.org/pep-0695/#type-parameter-scopes
T = 0

# T refers to the global variable
print(T)  # Prints 0

class Outer[T]:
    T = 1

    # T refers to the local variable scoped to class 'Outer'
    print(T)  # Prints 1

    class Inner1:
        T = 2

        # T refers to the local type variable within 'Inner1'
        print(T)  # Prints 2

        def inner_method(self):
            # T refers to the type parameter scoped to class 'Outer';
            # If 'Outer' did not use the new type parameter syntax,
            # this would instead refer to the global variable 'T'
            print(T)  # Prints 'T'
                  <ref>