from _typeshed import Incomplete
from enum import Enum
from typing import Final

from braintree.address import Address
from braintree.credit_card_verification import CreditCardVerification
from braintree.resource import Resource
from braintree.subscription import Subscription

class CreditCard(Resource):
    class CardType:
        AmEx: Final = "American Express"
        CarteBlanche: Final = "Carte Blanche"
        ChinaUnionPay: Final = "China UnionPay"
        DinersClubInternational: Final = "Diners Club"
        Discover: Final = "Discover"
        Electron: Final = "Electron"
        Elo: Final = "Elo"
        Hiper: Final = "Hiper"
        Hipercard: Final = "Hipercard"
        JCB: Final = "JCB"
        Laser: Final = "Laser"
        UK_Maestro: Final = "UK Maestro"
        Maestro: Final = "Maestro"
        MasterCard: Final = "MasterCard"
        Solo: Final = "Solo"
        Switch: Final = "Switch"
        Visa: Final = "Visa"
        Unknown: Final = "Unknown"

    class CustomerLocation:
        International: Final = "international"
        US: Final = "us"

    class CardTypeIndicator:
        Yes: Final = "Yes"
        No: Final = "No"
        Unknown: Final = "Unknown"

    class DebitNetwork(Enum):
        Accel = "ACCEL"
        Maestro = "MAESTRO"
        Nyce = "NYCE"
        Pulse = "PULSE"
        Star = "STAR"
        Star_Access = "STAR_ACCESS"

    Commercial: type[CardTypeIndicator]
    DurbinRegulated: type[CardTypeIndicator]
    Debit: type[CardTypeIndicator]
    Healthcare: type[CardTypeIndicator]
    CountryOfIssuance: type[CardTypeIndicator]
    IssuingBank: type[CardTypeIndicator]
    Payroll: type[CardTypeIndicator]
    Prepaid: type[CardTypeIndicator]
    ProductId: type[CardTypeIndicator]
    @staticmethod
    def create(params: Incomplete | None = None): ...
    @staticmethod
    def update(credit_card_token, params: Incomplete | None = None): ...
    @staticmethod
    def delete(credit_card_token): ...
    @staticmethod
    def expired(): ...
    @staticmethod
    def expiring_between(start_date, end_date): ...
    @staticmethod
    def find(credit_card_token): ...
    @staticmethod
    def from_nonce(nonce): ...
    @staticmethod
    def create_signature(): ...
    @staticmethod
    def update_signature(): ...
    @staticmethod
    def signature(type): ...
    is_expired = expired
    billing_address: Address | None
    subscriptions: list[Subscription]
    verification: CreditCardVerification
    def __init__(self, gateway, attributes): ...
    @property
    def expiration_date(self): ...
    @property
    def masked_number(self): ...
