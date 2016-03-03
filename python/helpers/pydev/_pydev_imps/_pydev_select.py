from select import *

try:
    from gevent import monkey  # @UnresolvedImport
    saved = monkey.saved['select']
    for key, val in saved.items():
        globals()[key] = val
except:
    pass