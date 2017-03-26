from threading import Thread
from time import sleep


def print_with_pause(text, pause):
    sleep(pause)
    print(text)


def fun1(n):
    while True:
        print_with_pause("Thread1", 0.1)


def fun2(m):
    sleep(2)
    while True:
        print_with_pause("Thread2", 0.1)  # breakpoint


threads = [Thread(target=fun1, args=(24,), name="Thread1"),
           Thread(target=fun2, args=(42,), name="Thread2")]

for thread in threads:
    thread.start()
