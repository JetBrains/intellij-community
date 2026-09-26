from _typeshed import Incomplete
from typing import TypedDict, type_check_only

from braintree.graphql.inputs.monetary_amount_input import _GraphqlVariables as _MonetaryAmountGraphqlVariables
from braintree.graphql.inputs.payer_info_input import _GraphqlVariables as _PayerInfoGraphqlVariables

@type_check_only
class _PaymentContext(TypedDict, total=False):
    amount: _MonetaryAmountGraphqlVariables
    cancelUrl: str
    countryCode: str
    expiryDate: str
    merchantAccountId: str
    orderId: str
    payerInfo: _PayerInfoGraphqlVariables
    returnUrl: str
    type: str

@type_check_only
class _GraphqlVariables(TypedDict):
    paymentContext: _PaymentContext

class CreateLocalPaymentContextInput:
    def __init__(
        self,
        amount: dict[str, Incomplete] | None = None,
        cancel_url: str | None = None,
        country_code: str | None = None,
        expiry_date: str | None = None,
        merchant_account_id: str | None = None,
        order_id: str | None = None,
        payer_info: dict[str, Incomplete] | None = None,
        return_url: str | None = None,
        type: str | None = None,
    ) -> None: ...
    def to_graphql_variables(self) -> _GraphqlVariables: ...
