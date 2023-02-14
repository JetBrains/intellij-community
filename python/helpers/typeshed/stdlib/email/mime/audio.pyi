from collections.abc import Callable
from email import _ParamsType
from email.mime.nonmultipart import MIMENonMultipart
from email.policy import Policy

__all__ = ["MIMEAudio"]

class MIMEAudio(MIMENonMultipart):
    def __init__(
        self,
        _audiodata: str | bytes,
        _subtype: str | None = ...,
        _encoder: Callable[[MIMEAudio], object] = ...,
        *,
        policy: Policy | None = ...,
        **_params: _ParamsType,
    ) -> None: ...
