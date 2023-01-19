from collections.abc import Callable
from email import _ParamsType
from email.mime.nonmultipart import MIMENonMultipart
from email.policy import Policy

__all__ = ["MIMEApplication"]

class MIMEApplication(MIMENonMultipart):
    def __init__(
        self,
        _data: str | bytes,
        _subtype: str = ...,
        _encoder: Callable[[MIMEApplication], object] = ...,
        *,
        policy: Policy | None = ...,
        **_params: _ParamsType,
    ) -> None: ...
