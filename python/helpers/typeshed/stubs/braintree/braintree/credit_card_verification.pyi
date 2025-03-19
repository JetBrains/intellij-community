from _typeshed import Incomplete
from decimal import Decimal
from typing import Final

from braintree.attribute_getter import AttributeGetter
from braintree.risk_data import RiskData
from braintree.three_d_secure_info import ThreeDSecureInfo

class CreditCardVerification(AttributeGetter):
    class Status:
        Failed: Final = "failed"
        GatewayRejected: Final = "gateway_rejected"
        ProcessorDeclined: Final = "processor_declined"
        Verified: Final = "verified"

    amount: Decimal | None
    currency_iso_code: Incomplete
    processor_response_code: Incomplete
    processor_response_text: Incomplete
    network_response_code: Incomplete
    network_response_text: Incomplete
    risk_data: RiskData | None
    three_d_secure_info: ThreeDSecureInfo | None
    def __init__(self, gateway, attributes) -> None: ...
    @staticmethod
    def find(verification_id): ...
    @staticmethod
    def search(*query): ...
    @staticmethod
    def create(params): ...
    @staticmethod
    def create_signature(): ...
    def __eq__(self, other): ...
