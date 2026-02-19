from re import Pattern
from typing import Any

from django.core.handlers.wsgi import WSGIHandler
from django.core.management.base import BaseCommand
from django.core.servers.basehttp import WSGIServer

naiveip_re: Pattern[str]

class Command(BaseCommand):
    default_addr: str
    default_addr_ipv6: str
    default_port: str
    protocol: str
    server_cls: type[WSGIServer]
    def run(self, **options: Any) -> None: ...
    def get_handler(self, *args: Any, **options: Any) -> WSGIHandler: ...
    def inner_run(self, *args: Any, **options: Any) -> None: ...
    def on_bind(self, server_port: int) -> None: ...
