from _typeshed import Incomplete

from braintree.util.http import Http

class GraphQLClient(Http):
    @staticmethod
    def raise_exception_for_graphql_error(response) -> None: ...
    graphql_headers: dict[str, str]
    def __init__(self, config: Incomplete | None = None, environment: Incomplete | None = None) -> None: ...
    def query(self, definition, variables: Incomplete | None = None, operation_name: Incomplete | None = None): ...
