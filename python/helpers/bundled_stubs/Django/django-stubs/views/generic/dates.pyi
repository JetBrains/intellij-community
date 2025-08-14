import datetime
from collections.abc import Sequence
from typing import Any, TypeVar

from django.db import models
from django.db.models.query import QuerySet
from django.http import HttpRequest, HttpResponse
from django.utils.datastructures import _IndexableCollection
from django.utils.functional import _Getter
from django.views.generic.base import View
from django.views.generic.detail import BaseDetailView, SingleObjectTemplateResponseMixin
from django.views.generic.list import MultipleObjectMixin, MultipleObjectTemplateResponseMixin
from typing_extensions import TypeAlias

_M = TypeVar("_M", bound=models.Model)
_DatedItems: TypeAlias = tuple[_IndexableCollection[datetime.date] | None, _IndexableCollection[_M], dict[str, Any]]

class YearMixin:
    year_format: str
    year: str | None
    def get_year_format(self) -> str: ...
    def get_year(self) -> str: ...
    def get_next_year(self, date: datetime.date) -> datetime.date | None: ...
    def get_previous_year(self, date: datetime.date) -> datetime.date | None: ...

class MonthMixin:
    month_format: str
    month: str | None
    def get_month_format(self) -> str: ...
    def get_month(self) -> str: ...
    def get_next_month(self, date: datetime.date) -> datetime.date | None: ...
    def get_previous_month(self, date: datetime.date) -> datetime.date | None: ...

class DayMixin:
    day_format: str
    day: str | None
    def get_day_format(self) -> str: ...
    def get_day(self) -> str: ...
    def get_next_day(self, date: datetime.date) -> datetime.date | None: ...
    def get_previous_day(self, date: datetime.date) -> datetime.date | None: ...

class WeekMixin:
    week_format: str
    week: str | None
    def get_week_format(self) -> str: ...
    def get_week(self) -> str: ...
    def get_next_week(self, date: datetime.date) -> datetime.date | None: ...
    def get_previous_week(self, date: datetime.date) -> datetime.date | None: ...

class DateMixin:
    date_field: str | None
    allow_future: bool
    def get_date_field(self) -> str: ...
    def get_allow_future(self) -> bool: ...
    uses_datetime_field: _Getter[bool] | bool

class BaseDateListView(MultipleObjectMixin[_M], DateMixin, View):
    date_list_period: str
    def get(self, request: HttpRequest, *args: Any, **kwargs: Any) -> HttpResponse: ...
    def get_dated_items(self) -> _DatedItems: ...
    def get_ordering(self) -> str | Sequence[str]: ...
    def get_dated_queryset(self, **lookup: Any) -> models.query.QuerySet[_M]: ...
    def get_date_list_period(self) -> str: ...
    def get_date_list(
        self, queryset: models.query.QuerySet, date_type: str | None = ..., ordering: str = ...
    ) -> models.query.QuerySet: ...

class BaseArchiveIndexView(BaseDateListView[_M]):
    context_object_name: str
    def get_dated_items(self) -> _DatedItems[_M]: ...

class ArchiveIndexView(MultipleObjectTemplateResponseMixin, BaseArchiveIndexView):
    template_name_suffix: str

class BaseYearArchiveView(YearMixin, BaseDateListView[_M]):
    date_list_period: str
    make_object_list: bool
    def get_dated_items(self) -> _DatedItems[_M]: ...
    def get_make_object_list(self) -> bool: ...

class YearArchiveView(MultipleObjectTemplateResponseMixin, BaseYearArchiveView):
    template_name_suffix: str

class BaseMonthArchiveView(YearMixin, MonthMixin, BaseDateListView[_M]):
    date_list_period: str
    def get_dated_items(self) -> _DatedItems[_M]: ...

class MonthArchiveView(MultipleObjectTemplateResponseMixin, BaseMonthArchiveView):
    template_name_suffix: str

class BaseWeekArchiveView(YearMixin, WeekMixin, BaseDateListView[_M]):
    def get_dated_items(self) -> _DatedItems[_M]: ...

class WeekArchiveView(MultipleObjectTemplateResponseMixin, BaseWeekArchiveView):
    template_name_suffix: str

class BaseDayArchiveView(YearMixin, MonthMixin, DayMixin, BaseDateListView[_M]):
    def get_dated_items(self) -> _DatedItems[_M]: ...

class DayArchiveView(MultipleObjectTemplateResponseMixin, BaseDayArchiveView):
    template_name_suffix: str

class BaseTodayArchiveView(BaseDayArchiveView[_M]):
    def get_dated_items(self) -> _DatedItems[_M]: ...

class TodayArchiveView(MultipleObjectTemplateResponseMixin, BaseTodayArchiveView):
    template_name_suffix: str

class BaseDateDetailView(YearMixin, MonthMixin, DayMixin, DateMixin, BaseDetailView[_M]):
    def get_object(self, queryset: QuerySet[_M] | None = ...) -> _M: ...

class DateDetailView(SingleObjectTemplateResponseMixin, BaseDateDetailView):
    template_name_suffix: str

def timezone_today() -> datetime.date: ...
