import m1
import m2


def foo(use):
    if use:
        runner = m1
    else:
        runner = m2

    make_barrier(runner)


def make_barrier(runner):
    return runner.Barrier(2)