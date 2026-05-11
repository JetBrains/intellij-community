from typing import TypedDict, type_check_only
from typing_extensions import Self

from braintree.graphql.inputs.customer_session_input import (
    CustomerSessionInput,
    _GraphqlVariables as _CustomerSessionGraphqlVariables,
)
from braintree.graphql.inputs.paypal_purchase_unit_input import (
    PayPalPurchaseUnitInput,
    _GraphqlVariables as _PayPalPurchaseUnitGraphqlVariables,
)

@type_check_only
class _GraphqlVariables(TypedDict, total=False):
    merchantAccountId: str
    sessionId: str
    customer: _CustomerSessionGraphqlVariables
    domain: str
    purchaseUnits: list[_PayPalPurchaseUnitGraphqlVariables]

class CreateCustomerSessionInput:
    def __init__(
        self,
        merchant_account_id: str | None = None,
        session_id: str | None = None,
        customer: CustomerSessionInput | None = None,
        domain: str | None = None,
        purchase_units: list[PayPalPurchaseUnitInput] | None = None,
    ) -> None: ...
    def to_graphql_variables(self) -> _GraphqlVariables: ...
    @staticmethod
    def builder() -> Builder: ...

    class Builder:
        def __init__(self) -> None: ...
        def merchant_account_id(self, merchant_account_id: str) -> Self: ...
        def session_id(self, session_id: str) -> Self: ...
        def customer(self, customer: CustomerSessionInput) -> Self: ...
        def purchase_units(self, purchase_units: list[PayPalPurchaseUnitInput]) -> Self: ...
        def domain(self, domain: str) -> Self: ...
        def build(self) -> CreateCustomerSessionInput: ...
