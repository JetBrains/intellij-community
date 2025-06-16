from _typeshed import Incomplete

from braintree.error_result import ErrorResult
from braintree.resource import Resource
from braintree.successful_result import SuccessfulResult

class PaymentMethod(Resource):
    @staticmethod
    def create(params: dict[str, Incomplete] | None = None) -> SuccessfulResult | ErrorResult: ...
    @staticmethod
    def find(payment_method_token: str) -> Resource: ...
    @staticmethod
    def update(payment_method_token: str, params) -> SuccessfulResult | ErrorResult: ...
    @staticmethod
    def delete(payment_method_token: str, options=None) -> SuccessfulResult: ...
    @staticmethod
    def create_signature() -> (
        list[
            str
            | dict[str, list[str | dict[str, list[str]]]]
            | dict[str, list[str | dict[str, list[str]] | dict[str, list[str | dict[str, list[str | dict[str, list[str]]]]]]]]
            | dict[str, list[str]]
        ]
    ): ...
    @staticmethod
    def signature(
        type: str,
    ) -> list[
        str
        | dict[str, list[str | dict[str, list[str]]]]
        | dict[str, list[str | dict[str, list[str]] | dict[str, list[str | dict[str, list[str | dict[str, list[str]]]]]]]]
        | dict[str, list[str]]
    ]: ...
    @staticmethod
    def update_signature() -> list[str | dict[str, list[str | dict[str, list[str]]]] | dict[str, list[str]]]: ...
    @staticmethod
    def delete_signature() -> list[str]: ...
