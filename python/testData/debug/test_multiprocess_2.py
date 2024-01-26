from __future__ import with_statement
import multiprocessing


def f(x):
    return x*x  # breakpoint


if __name__ == '__main__':
    with multiprocessing.Pool() as p:
        print(p.map(f, [1, 2, 3]))
