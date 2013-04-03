from pydevd_constants import USE_LIB_COPY
try:
    try:
        if USE_LIB_COPY:
            import _pydev_xmlrpclib as xmlrpclib
        else:
            import xmlrpclib
    except ImportError:
        import xmlrpc.client as xmlrpclib
except ImportError:
    import _pydev_xmlrpclib as xmlrpclib
try:
    try:
        if USE_LIB_COPY:
            from _pydev_SimpleXMLRPCServer import SimpleXMLRPCServer
        else:
            from SimpleXMLRPCServer import SimpleXMLRPCServer
    except ImportError:
        from xmlrpc.server import SimpleXMLRPCServer
except ImportError:
    from _pydev_SimpleXMLRPCServer import SimpleXMLRPCServer
try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO
try:
    execfile=execfile #Not in Py3k
except NameError:
    from _pydev_execfile import execfile
try:
    if USE_LIB_COPY:
        import _pydev_Queue as _queue
    else:
        import Queue as _queue
except:
    import queue as _queue
