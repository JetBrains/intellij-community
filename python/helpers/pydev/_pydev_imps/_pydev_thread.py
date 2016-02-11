try:
    from thread import *
except:
    from _thread import * #Py3k

try:
    from gevent import monkey  # @UnresolvedImport
    saved = monkey.saved['thread']
    for key, val in saved.items():
        globals()[key] = val
except:
    pass
