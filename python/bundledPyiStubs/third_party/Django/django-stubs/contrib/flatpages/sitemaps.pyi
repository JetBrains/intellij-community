from django.contrib.flatpages.models import FlatPage
from django.contrib.sitemaps import Sitemap

class FlatPageSitemap(Sitemap[FlatPage]): ...
