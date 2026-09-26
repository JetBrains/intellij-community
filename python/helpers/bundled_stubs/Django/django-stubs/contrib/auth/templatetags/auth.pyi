from django.template import Library
from django.utils.safestring import SafeString

register: Library

def render_password_as_hash(value: str | None) -> SafeString: ...
