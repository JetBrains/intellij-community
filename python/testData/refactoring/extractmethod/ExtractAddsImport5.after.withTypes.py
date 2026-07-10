import signal
from signal import Handlers


def f(sign) :
    if sign is signal.Handlers.SIG_DFL:
        body(sign)


def body(sign_new: Handlers):
    1
    sign_new
