from django.core.management.base import BaseCommand

has_bz2: bool
has_lzma: bool

class ProxyModelWarning(Warning): ...
class Command(BaseCommand): ...
