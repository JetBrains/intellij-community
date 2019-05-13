from django.db import models
class BasePage(models.Model):
    title = models.CharField(max_length=50)
    slug = models.CharField(max_length=50)


    class Meta:
            abstract = True
