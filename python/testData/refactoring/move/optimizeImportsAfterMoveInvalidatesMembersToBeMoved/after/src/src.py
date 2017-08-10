# -*- coding: utf-8 -*-
# (c) 2017 Tuomas Airaksinen
#
# This file is part of Serviceform.
#
# Serviceform is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Serviceform is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Serviceform.  If not, see <http://www.gnu.org/licenses/>.
import datetime

import logging
import string
from colorful.fields import RGBColorField
from django.conf import settings
from django.contrib.contenttypes.fields import GenericRelation
from django.db import models
from django.db.models import Prefetch
from django.template.loader import render_to_string
from django.urls import reverse
from django.utils import timezone
from django.utils.functional import cached_property
from django.utils.html import format_html
from django.utils.translation import ugettext_lazy as _
from enum import Enum
from guardian.shortcuts import get_users_with_perms
from select2 import fields as select2_fields
from serviceform.tasks.models import Task
from typing import Tuple, Set, Optional, Sequence, Iterator, Iterable, TYPE_CHECKING

from .email import EmailTemplate
from .mixins import CopyMixin
from .participation import QuestionAnswer
from .people import Participant, ResponsibilityPerson
from .. import emails, utils
from ..utils import ColorStr

if TYPE_CHECKING:
    from .participation import ParticipationActivity, ParticipationActivityChoice

local_tz = timezone.get_default_timezone()
logger = logging.getLogger(__name__)


def imported_symbols_anchor():
    print(RGBColorField, settings, GenericRelation, Prefetch, render_to_string, reverse, format_html,
          get_users_with_perms, select2_fields, Task, emails, CopyMixin, Participant, ResponsibilityPerson,
          EmailTemplate, QuestionAnswer, ParticipationActivity, ParticipationActivityChoice, datetime, Enum, string,
          Tuple, Set, Optional, Sequence, Iterator, Iterable, _, cached_property, models, utils, ColorStr)
