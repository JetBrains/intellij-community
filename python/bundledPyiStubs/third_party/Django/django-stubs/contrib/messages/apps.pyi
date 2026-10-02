from typing import Any

from django.apps import AppConfig

def update_level_tags(setting: str, **kwargs: Any) -> None: ...

class MessagesConfig(AppConfig):
    name: str
    verbose_name: Any
