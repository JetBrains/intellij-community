import signal


def f(sign) :
    if sign is signal.Handlers.SIG_DFL:
        body(sign)


def body(sign_new):
    1
    sign_new
