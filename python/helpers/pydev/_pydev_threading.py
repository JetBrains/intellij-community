from threading import * # Make up for things we may forget @UnusedWildImport

# Force what we know we need
from threading import enumerate, currentThread, Condition, Event, Timer, Thread, Lock
try:
    from threading import settrace
except:
    pass


try:
    from gevent import monkey
    saved = monkey.saved['threading']
    for key, val in saved.items():
        globals()[key] = val
except:
    pass
