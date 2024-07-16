
@_SpecialForm
def Final(self, parameters):
    """Special typing construct to indicate final names to type checkers.

    A final name cannot be re-assigned or overridden in a subclass.

    For example::

        MAX_SIZE: Final = 9000
        MAX_SIZE += 1  # Error reported by type checker

        class Connection:
            TIMEOUT: Final[int] = 10

        class FastConnector(Connection):
            TIMEOUT = 1  # Error reported by type checker

    There is no runtime checking of these properties.
    """
    item = _type_check(parameters, f'{self} accepts only single type.')
    return _GenericAlias(self, (item,))