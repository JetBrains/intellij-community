from threading import Thread, RLock
from time import sleep
lock = RLock()


def foo():
    sleep(1)
    while True:
        lock.acquire()
        x = 12
        sleep(0.01)
        lock.release()
    print("finished foo()", x)


threads = [Thread(target=foo, name="Thread1"),
           Thread(target=foo, name="Thread2")]

for thread in threads:
    thread.start()