from _pydevd_bundle.pydevd_constants import USE_LIB_COPY, izip


try:
    try:
        if USE_LIB_COPY:
            from _pydev_imps._pydev_saved_modules import xmlrpclib
        else:
            import xmlrpclib
    except ImportError:
        import xmlrpc.client as xmlrpclib
except ImportError:
    from _pydev_imps import _pydev_xmlrpclib as xmlrpclib


try:
    try:
        if USE_LIB_COPY:
            from _pydev_imps._pydev_saved_modules import _pydev_SimpleXMLRPCServer
            from _pydev_SimpleXMLRPCServer import SimpleXMLRPCServer
        else:
            from SimpleXMLRPCServer import SimpleXMLRPCServer
    except ImportError:
        from xmlrpc.server import SimpleXMLRPCServer
except ImportError:
    from _pydev_imps._pydev_SimpleXMLRPCServer import SimpleXMLRPCServer



try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO


try:
    execfile=execfile #Not in Py3k
except NameError:
    from _pydev_imps._pydev_execfile import execfile


try:
    if USE_LIB_COPY:
        from _pydev_imps._pydev_saved_modules import _queue
    else:
        import Queue as _queue
except:
    import queue as _queue #@UnresolvedImport


try:
    from _pydevd_bundle.pydevd_exec import Exec
except:
    from _pydevd_bundle.pydevd_exec2 import Exec

try:
    from urllib import quote, quote_plus, unquote_plus
except:
    from urllib.parse import quote, quote_plus, unquote_plus #@UnresolvedImport

