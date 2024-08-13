from collections.abc import Iterable
from typing import Any, ClassVar, Literal, TypeVar

from django.contrib.auth.base_user import AbstractBaseUser as AbstractBaseUser
from django.contrib.auth.base_user import BaseUserManager as BaseUserManager
from django.contrib.auth.validators import UnicodeUsernameValidator
from django.contrib.contenttypes.models import ContentType
from django.db import models
from django.db.models import QuerySet
from django.db.models.base import Model
from django.db.models.manager import EmptyManager
from django.utils.functional import _StrOrPromise
from typing_extensions import Self, TypeAlias

_AnyUser: TypeAlias = Model | AnonymousUser

def update_last_login(sender: type[AbstractBaseUser], user: AbstractBaseUser, **kwargs: Any) -> None: ...

class PermissionManager(models.Manager[Permission]):
    def get_by_natural_key(self, codename: str, app_label: str, model: str) -> Permission: ...

class Permission(models.Model):
    content_type_id: int
    objects: ClassVar[PermissionManager]

    name = models.CharField(max_length=255)
    content_type = models.ForeignKey(ContentType, on_delete=models.CASCADE)
    codename = models.CharField(max_length=100)
    def natural_key(self) -> tuple[str, str, str]: ...

class GroupManager(models.Manager[Group]):
    def get_by_natural_key(self, name: str) -> Group: ...

class Group(models.Model):
    objects: ClassVar[GroupManager]

    name = models.CharField(max_length=150)
    permissions = models.ManyToManyField(Permission)
    def natural_key(self) -> tuple[str]: ...

_T = TypeVar("_T", bound=Model)

class UserManager(BaseUserManager[_T]):
    def create_user(
        self, username: str, email: str | None = ..., password: str | None = ..., **extra_fields: Any
    ) -> _T: ...
    def create_superuser(
        self, username: str, email: str | None = ..., password: str | None = ..., **extra_fields: Any
    ) -> _T: ...
    def with_perm(
        self,
        perm: str | Permission,
        is_active: bool = ...,
        include_superusers: bool = ...,
        backend: str | None = ...,
        obj: Model | None = ...,
    ) -> QuerySet[_T]: ...

class PermissionsMixin(models.Model):
    is_superuser = models.BooleanField()
    groups = models.ManyToManyField(Group)
    user_permissions = models.ManyToManyField(Permission)

    def get_user_permissions(self, obj: _AnyUser | None = ...) -> set[str]: ...
    def get_group_permissions(self, obj: _AnyUser | None = ...) -> set[str]: ...
    def get_all_permissions(self, obj: _AnyUser | None = ...) -> set[str]: ...
    def has_perm(self, perm: str, obj: _AnyUser | None = ...) -> bool: ...
    def has_perms(self, perm_list: Iterable[str], obj: _AnyUser | None = ...) -> bool: ...
    def has_module_perms(self, app_label: str) -> bool: ...

class AbstractUser(AbstractBaseUser, PermissionsMixin):
    username_validator: UnicodeUsernameValidator

    username = models.CharField(max_length=150)
    first_name = models.CharField(max_length=30, blank=True)
    last_name = models.CharField(max_length=150, blank=True)
    email = models.EmailField(blank=True)
    is_staff = models.BooleanField()
    is_active = models.BooleanField()
    date_joined = models.DateTimeField()

    objects: ClassVar[UserManager[Self]]

    EMAIL_FIELD: str
    USERNAME_FIELD: str

    def get_full_name(self) -> str: ...
    def get_short_name(self) -> str: ...
    def email_user(
        self, subject: _StrOrPromise, message: _StrOrPromise, from_email: str = ..., **kwargs: Any
    ) -> None: ...

class User(AbstractUser): ...

class AnonymousUser:
    id: Any
    pk: Any
    username: Literal[""]
    is_staff: Literal[False]
    is_active: Literal[False]
    is_superuser: Literal[False]
    def save(self) -> None: ...
    def delete(self) -> None: ...
    def set_password(self, raw_password: str) -> None: ...
    def check_password(self, raw_password: str) -> Any: ...
    @property
    def groups(self) -> EmptyManager[Group]: ...
    @property
    def user_permissions(self) -> EmptyManager[Permission]: ...
    def get_user_permissions(self, obj: _AnyUser | None = ...) -> set[str]: ...
    def get_group_permissions(self, obj: _AnyUser | None = ...) -> set[Any]: ...
    def get_all_permissions(self, obj: _AnyUser | None = ...) -> set[str]: ...
    def has_perm(self, perm: str, obj: _AnyUser | None = ...) -> bool: ...
    def has_perms(self, perm_list: Iterable[str], obj: _AnyUser | None = ...) -> bool: ...
    def has_module_perms(self, module: str) -> bool: ...
    @property
    def is_anonymous(self) -> Literal[True]: ...
    @property
    def is_authenticated(self) -> Literal[False]: ...
    def get_username(self) -> str: ...
