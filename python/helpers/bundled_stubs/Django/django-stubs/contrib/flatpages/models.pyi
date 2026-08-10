from django.contrib.sites.models import Site
from django.db import models
from django.db.models.expressions import Combinable

class FlatPage(models.Model):
    url: models.CharField[str | int | Combinable, str]
    title: models.CharField[str | int | Combinable, str]
    content: models.TextField[str | Combinable, str]
    enable_comments: models.BooleanField[bool | Combinable, bool]
    template_name: models.CharField[str | int | Combinable, str]
    registration_required: models.BooleanField[bool | Combinable, bool]
    sites = models.ManyToManyField(Site)
    def get_absolute_url(self) -> str: ...
