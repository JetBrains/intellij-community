from decimal import Decimal
from typing import TypedDict, type_check_only

@type_check_only
class _GraphqlVariables(TypedDict, total=False):
    value: str
    currencyCode: str

class MonetaryAmountInput:
    def __init__(self, value: Decimal | None = None, currency_code: str | None = None) -> None: ...
    def to_graphql_variables(self) -> _GraphqlVariables: ...
