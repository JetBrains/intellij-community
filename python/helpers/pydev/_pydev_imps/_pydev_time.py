from time import *

try:
    from gevent import monkey  # @UnresolvedImport
    saved = monkey.saved['time']
    for key, val in saved.items():
        globals()[key] = val
except:
    pass
