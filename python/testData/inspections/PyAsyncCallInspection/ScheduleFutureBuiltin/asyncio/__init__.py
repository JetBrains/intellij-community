from .futures import *
from .tasks import *
from .events import *


def sleep(s):
    pass


def ensure_future(coro) -> Future:
    pass


def get_running_loop() -> AbstractEventLoop:
    pass
