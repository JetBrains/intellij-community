from _typeshed import Incomplete

from braintree.error_result import ErrorResult
from braintree.graphql import CreateLocalPaymentContextInput
from braintree.successful_result import SuccessfulResult

class LocalPaymentContextGateway:
    CREATE_LOCAL_PAYMENT_CONTEXT: str
    FIND_LOCAL_PAYMENT_CONTEXT: str
    gateway: Incomplete
    graphql_client: Incomplete
    def __init__(self, gateway) -> None: ...
    def create(self, input: CreateLocalPaymentContextInput) -> SuccessfulResult | ErrorResult: ...
    def find(self, id) -> SuccessfulResult | ErrorResult: ...
