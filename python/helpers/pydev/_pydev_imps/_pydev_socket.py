from socket import *

try:
    from gevent import monkey
    saved = monkey.saved['socket']
    for key, val in saved.items():
        globals()[key] = val
except:
    pass