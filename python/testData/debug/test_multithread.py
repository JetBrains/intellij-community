try:
    import thread
except :
    import _thread as thread

import threading

def bar(y):
    z = 100 + y
    print("Z=%d"%z)

t = None
def foo(x):
    global t
    y = x + 1
    print("Y=%d"%y)

    t = threading.Thread(target=bar, args=(y,))
    t.start()


id = thread.start_new_thread(foo, (1,))

while True:
    pass

