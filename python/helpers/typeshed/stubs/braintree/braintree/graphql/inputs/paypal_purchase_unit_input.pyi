from typing import TypedDict, type_check_only
from typing_extensions import Self

from braintree.graphql.inputs.monetary_amount_input import (
    MonetaryAmountInput,
    _GraphqlVariables as _MonetaryAmountGraphqlVariables,
)
from braintree.graphql.inputs.paypal_payee_input import PayPalPayeeInput, _GraphqlVariables as _PayPalPayeeGraphqlVariables

@type_check_only
class _GraphqlVariables(TypedDict, total=False):
    payee: _PayPalPayeeGraphqlVariables
    amount: _MonetaryAmountGraphqlVariables

class PayPalPurchaseUnitInput:
    def __init__(self, amount: MonetaryAmountInput | None = None, payee: PayPalPayeeInput | None = None) -> None: ...
    def to_graphql_variables(self) -> _GraphqlVariables: ...
    @staticmethod
    def builder(amount: MonetaryAmountInput) -> Builder: ...

    class Builder:
        def __init__(self, amount: MonetaryAmountInput) -> None: ...
        def payee(self, payee: PayPalPayeeInput) -> Self: ...
        def build(self) -> PayPalPurchaseUnitInput: ...
