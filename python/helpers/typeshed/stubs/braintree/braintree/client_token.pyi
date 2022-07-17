from typing import Any

from braintree import exceptions as exceptions
from braintree.configuration import Configuration as Configuration
from braintree.signature_service import SignatureService as SignatureService
from braintree.util.crypto import Crypto as Crypto

class ClientToken:
    @staticmethod
    def generate(params: Any | None = ..., gateway: Any | None = ...): ...
    @staticmethod
    def generate_signature(): ...
