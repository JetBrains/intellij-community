from typing import TypedDict, type_check_only

@type_check_only
class _SupportedWorkers(TypedDict):
    sync: str
    eventlet: str
    gevent: str
    gevent_wsgi: str
    gevent_pywsgi: str
    tornado: str
    gthread: str

SUPPORTED_WORKERS: _SupportedWorkers
