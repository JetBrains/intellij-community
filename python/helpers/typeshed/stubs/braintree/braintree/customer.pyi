from _typeshed import Incomplete

from braintree.address import Address
from braintree.amex_express_checkout_card import AmexExpressCheckoutCard
from braintree.android_pay_card import AndroidPayCard
from braintree.apple_pay_card import ApplePayCard
from braintree.credit_card import CreditCard
from braintree.europe_bank_account import EuropeBankAccount
from braintree.masterpass_card import MasterpassCard
from braintree.paypal_account import PayPalAccount
from braintree.resource import Resource
from braintree.samsung_pay_card import SamsungPayCard
from braintree.us_bank_account import UsBankAccount
from braintree.venmo_account import VenmoAccount
from braintree.visa_checkout_card import VisaCheckoutCard

class Customer(Resource):
    @staticmethod
    def all(): ...
    @staticmethod
    def create(params: Incomplete | None = None): ...
    @staticmethod
    def delete(customer_id): ...
    @staticmethod
    def find(customer_id, association_filter_id: Incomplete | None = None): ...
    @staticmethod
    def search(*query): ...
    @staticmethod
    def update(customer_id, params: Incomplete | None = None): ...
    @staticmethod
    def create_signature(): ...
    @staticmethod
    def update_signature(): ...
    payment_methods: list[Resource]
    credit_cards: list[CreditCard]
    addresses: list[Address]
    paypal_accounts: list[PayPalAccount]
    apple_pay_cards: list[ApplePayCard]
    android_pay_cards: list[AndroidPayCard]
    amex_express_checkout_cards: list[AmexExpressCheckoutCard]
    europe_bank_accounts: list[EuropeBankAccount]
    venmo_accounts: list[VenmoAccount]
    us_bank_accounts: list[UsBankAccount]
    visa_checkout_cards: list[VisaCheckoutCard]
    masterpass_cards: list[MasterpassCard]
    samsung_pay_cards: list[SamsungPayCard]
    def __init__(self, gateway, attributes) -> None: ...
