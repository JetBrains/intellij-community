import signal
from signal import Handlers
from typing import Literal


def f(sign) :
    if sign is signal.Handlers.SIG_DFL:
        body(sign)


def body(sign_new: Literal[Handlers.SIG_DFL]):
    1
    sign_new
