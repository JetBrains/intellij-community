try:
    try:
        import xmlrpclib
    except ImportError:
        import xmlrpc.client as xmlrpclib
except ImportError:
    import _pydev_xmlrpclib as xmlrpclib
try:
    try:
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
    import Queue
except:
    import queue as Queue
