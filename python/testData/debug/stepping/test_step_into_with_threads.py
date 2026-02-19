from __future__ import print_function
import threading


class A(threading.Thread):
    def foo(self):
        print("foo")

    def bar(self):
        print("bar")

    def baz(self):
        print("baz")

    def run(self):
        self.foo()  # breakpoint
        self.bar()
        self.baz()  # breakpoint


if __name__ == '__main__':
    t = A()
    t.start()
    t.join()