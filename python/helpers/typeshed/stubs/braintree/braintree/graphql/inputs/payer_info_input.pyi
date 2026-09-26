from _typeshed import Incomplete
from typing import TypedDict, type_check_only

from braintree.graphql.inputs.billing_address_input import _GraphqlVariables as _BillingAddressGraphqlVariables
from braintree.graphql.inputs.shipping_address_input import _GraphqlVariables as _ShippingAddressGraphqlVariables

@type_check_only
class _GraphqlVariables(TypedDict, total=False):
    billingAddress: _BillingAddressGraphqlVariables
    email: str
    givenName: str
    phoneCountryCode: str
    phoneNumber: str
    shippingAddress: _ShippingAddressGraphqlVariables
    surname: str

class PayerInfoInput:
    def __init__(
        self,
        billing_address: dict[str, Incomplete] | None = None,
        email: str | None = None,
        given_name: str | None = None,
        phone_country_code: str | None = None,
        phone_number: str | None = None,
        shipping_address: dict[str, Incomplete] | None = None,
        surname: str | None = None,
    ) -> None: ...
    def to_graphql_variables(self) -> _GraphqlVariables: ...
