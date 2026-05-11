from typing import TypedDict, type_check_only

@type_check_only
class _GraphqlVariables(TypedDict, total=False):
    countryCode: str
    extendedAddress: str
    locality: str
    postalCode: str
    region: str
    streetAddress: str

class BillingAddressInput:
    def __init__(
        self,
        country_code_alpha2: str | None = None,
        extended_address: str | None = None,
        locality: str | None = None,
        postal_code: str | None = None,
        region: str | None = None,
        street_address: str | None = None,
    ) -> None: ...
    def to_graphql_variables(self) -> _GraphqlVariables: ...
