import lib


class Factory:
    def __call__(self, p):
        """
        MyClass function_like(int)
        """


# Emulate synthetic non-function callable where the signature
# and the stub return value are restored from its docstring.
function_like = Factory().__call__

del Factory
