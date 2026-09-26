from _typeshed import Incomplete
from typing_extensions import deprecated

from braintree.error_result import ErrorResult
from braintree.successful_result import SuccessfulResult

class MerchantGateway:
    gateway: Incomplete
    config: Incomplete
    def __init__(self, gateway) -> None: ...
    @deprecated("gateway.merchant.create(...) is deprecated and will be removed in a future version.")
    def create(self, params: dict[str, Incomplete] | None) -> SuccessfulResult | ErrorResult: ...
