import xlogging.handlers
import xlogging

class MultiProcessingLog(xlogging.Handler):
    def __init__(self, *args):
        xlogging.Handler.__init__(self)
        self._handler = xlogging.handlers.RotatingFileHandler(*args)

MultiProcessingLog('a','a',1000,3,'utf8')
