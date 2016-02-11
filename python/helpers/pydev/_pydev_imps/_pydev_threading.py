from threading import * # Make up for things we may forget @UnusedWildImport

# Force what we know we need
from threading import enumerate, currentThread, Condition, Event, Thread, Lock
try:
    from threading import settrace
except:
    pass
try:
    from threading import Timer
except:
    pass # Jython 2.1


try:
    from gevent import monkey  # @UnresolvedImport
    saved = monkey.saved['threading']
    for key, val in saved.items():
        globals()[key] = val
except:
    pass
