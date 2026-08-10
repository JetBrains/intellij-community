from django.contrib.sites.models import Site
from django.db import models
from django.db.models.expressions import Combinable

class Redirect(models.Model):
    site: models.ForeignKey[Site | Combinable, Site]
    old_path: models.CharField[str | int | Combinable, str]
    new_path: models.CharField[str | int | Combinable, str]
