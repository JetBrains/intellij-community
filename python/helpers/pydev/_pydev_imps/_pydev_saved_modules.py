import sys
IS_PY2 = True
if sys.version_info[0] >= 3:
    IS_PY2 = False

import threading

if IS_PY2:
    import thread
else:
    import _thread as thread

import time

import socket

import select

if IS_PY2:
    import Queue as _queue
else:
    import queue as _queue

if IS_PY2:
    import xmlrpclib
else:
    import xmlrpc.client as xmlrpclib

if IS_PY2:
    import SimpleXMLRPCServer as _pydev_SimpleXMLRPCServer
else:
    import xmlrpc.server as _pydev_SimpleXMLRPCServer

if IS_PY2:
    import BaseHTTPServer
else:
    import http.server as BaseHTTPServer