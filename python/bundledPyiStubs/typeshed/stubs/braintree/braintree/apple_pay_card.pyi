from _typeshed import Incomplete
from typing import Final

from braintree.credit_card_verification import CreditCardVerification
from braintree.resource import Resource
from braintree.subscription import Subscription

class ApplePayCard(Resource):
    class CardType:
        AmEx: Final = "Apple Pay - American Express"
        MasterCard: Final = "Apple Pay - MasterCard"
        Visa: Final = "Apple Pay - Visa"

    is_expired: Incomplete
    subscriptions: list[Subscription]
    verification: CreditCardVerification | None
    def __init__(self, gateway, attributes) -> None: ...
    @property
    def expiration_date(self): ...
    @staticmethod
    def signature() -> list[str | dict[str, list[str]]]: ...
