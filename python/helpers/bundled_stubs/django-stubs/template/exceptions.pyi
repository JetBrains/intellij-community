from django.template.backends.base import BaseEngine
from django.template.base import Origin

class TemplateDoesNotExist(Exception):
    backend: BaseEngine | None
    tried: list[tuple[Origin, str]]
    chain: list[TemplateDoesNotExist]
    def __init__(
        self,
        msg: Origin | str,
        tried: list[tuple[Origin, str]] | None = ...,
        backend: BaseEngine | None = ...,
        chain: list[TemplateDoesNotExist] | None = ...,
    ) -> None: ...

class TemplateSyntaxError(Exception): ...
