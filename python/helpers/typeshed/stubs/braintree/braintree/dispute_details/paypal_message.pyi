from typing import Any

from braintree.attribute_getter import AttributeGetter

class DisputePayPalMessage(AttributeGetter):
    def __init__(self, attributes: dict[str, Any] | None) -> None: ...
