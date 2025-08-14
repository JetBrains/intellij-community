from .base import RedirectView as RedirectView
from .base import TemplateView as TemplateView
from .base import View as View
from .dates import ArchiveIndexView as ArchiveIndexView
from .dates import DateDetailView as DateDetailView
from .dates import DayArchiveView as DayArchiveView
from .dates import MonthArchiveView as MonthArchiveView
from .dates import TodayArchiveView as TodayArchiveView
from .dates import WeekArchiveView as WeekArchiveView
from .dates import YearArchiveView as YearArchiveView
from .detail import DetailView as DetailView
from .edit import CreateView as CreateView
from .edit import DeleteView as DeleteView
from .edit import FormView as FormView
from .edit import UpdateView as UpdateView
from .list import ListView as ListView

class GenericViewError(Exception): ...
