from threading import Thread
from time import sleep


def fun1(n):
    while True:
        sleep(0.01)
    print("finished fun1()", n)


def fun2(m):
    sleep(2)
    while True:
        sleep(0.01) #breakpoint
    print("finished fun2()", m)


threads = [Thread(target=fun1, args=(24,), name="Thread1"),
           Thread(target=fun2, args=(42,), name="Thread2")]

for thread in threads:
    thread.start()
