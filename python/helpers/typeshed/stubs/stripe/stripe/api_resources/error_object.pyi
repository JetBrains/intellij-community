from typing import Any

from stripe.stripe_object import StripeObject as StripeObject

class ErrorObject(StripeObject):
    def refresh_from(
        self,
        values,
        api_key: Any | None = ...,
        partial: bool = ...,
        stripe_version: Any | None = ...,
        stripe_account: Any | None = ...,
        last_response: Any | None = ...,
    ): ...

class OAuthErrorObject(StripeObject):
    def refresh_from(
        self,
        values,
        api_key: Any | None = ...,
        partial: bool = ...,
        stripe_version: Any | None = ...,
        stripe_account: Any | None = ...,
        last_response: Any | None = ...,
    ): ...
