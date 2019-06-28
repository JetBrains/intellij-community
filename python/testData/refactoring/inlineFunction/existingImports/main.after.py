from source import do_more_work
from source import do_work
from source import do_substantially_more_work as do_nothing


def bar():
    do_work()
    do_more_work(42)
    do_nothing()
    res = 42
