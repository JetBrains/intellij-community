from collections.abc import Callable

from django.apps.registry import Apps
from django.db.models.base import Model
from django.dispatch import Signal

class_prepared: Signal

class ModelSignal(Signal):
    def connect(  # type: ignore[override]
        self,
        receiver: Callable,
        sender: type[Model] | str | None = None,
        weak: bool = True,
        dispatch_uid: str | None = None,
        apps: Apps | None = None,
    ) -> None: ...
    def disconnect(  # type: ignore[override]
        self,
        receiver: Callable | None = None,
        sender: type[Model] | str | None = None,
        dispatch_uid: str | None = None,
        apps: Apps | None = None,
    ) -> bool | None: ...

pre_init: ModelSignal
post_init: ModelSignal
pre_save: ModelSignal
post_save: ModelSignal
pre_delete: ModelSignal
post_delete: ModelSignal
m2m_changed: ModelSignal
pre_migrate: Signal
post_migrate: Signal
