import threading

try:
    import thread
except:
    import _thread as thread

import time

import socket

import select

try:
    import Queue as _queue
except:
    import queue as _queue

try:
    import xmlrpclib
except:
    import xmlrpc.client as xmlrpclib

try:
    import SimpleXMLRPCServer as _pydev_SimpleXMLRPCServer
except:
    import xmlrpc.server as _pydev_SimpleXMLRPCServer

try:
    import BaseHTTPServer
except:
    import http.server as BaseHTTPServer