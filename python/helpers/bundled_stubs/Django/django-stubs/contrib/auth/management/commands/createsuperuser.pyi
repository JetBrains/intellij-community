import getpass as getpass
from typing import Any

from django.contrib.auth.base_user import AbstractBaseUser
from django.core.management.base import BaseCommand
from django.db.models import Field
from django.utils.functional import cached_property

class NotRunningInTTYException(Exception): ...

PASSWORD_FIELD: str

class Command(BaseCommand):
    UserModel: type[AbstractBaseUser]
    username_field: Field
    stdin: Any
    def get_input_data(self, field: Field, message: str, default: str | None = ...) -> str | None: ...
    @cached_property
    def username_is_unique(self) -> bool: ...
