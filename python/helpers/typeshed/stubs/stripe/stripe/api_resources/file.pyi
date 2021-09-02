from typing import Any

from stripe import api_requestor as api_requestor
from stripe.api_resources.abstract import ListableAPIResource as ListableAPIResource

class File(ListableAPIResource):
    OBJECT_NAME: str
    OBJECT_NAME_ALT: str
    @classmethod
    def class_url(cls): ...
    @classmethod
    def create(
        cls,
        api_key: Any | None = ...,
        api_version: Any | None = ...,
        stripe_version: Any | None = ...,
        stripe_account: Any | None = ...,
        **params,
    ): ...

FileUpload = File
