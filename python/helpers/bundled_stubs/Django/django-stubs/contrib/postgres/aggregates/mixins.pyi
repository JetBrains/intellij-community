from typing import Any, ClassVar

from typing_extensions import override

class OrderableAggMixin:
    allow_order_by: ClassVar[bool]
    @override
    def __init_subclass__(cls, /, *args: Any, **kwargs: Any) -> None: ...
