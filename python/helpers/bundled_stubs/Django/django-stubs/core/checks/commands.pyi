from typing import Any

from django.core.checks.messages import Error

def migrate_and_makemigrations_autodetector(**kwargs: Any) -> list[Error]: ...
