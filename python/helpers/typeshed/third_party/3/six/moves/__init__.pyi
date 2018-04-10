# Stubs for six.moves
#
# Note: Commented out items means they weren't implemented at the time.
# Uncomment them when the modules have been added to the typeshed.
import sys

from io import StringIO as cStringIO
from builtins import filter as filter
from itertools import filterfalse as filterfalse
from builtins import input as input
from sys import intern as intern
from builtins import map as map
from os import getcwd as getcwd
from os import getcwdb as getcwdb
from builtins import range as range
from functools import reduce as reduce
from shlex import quote as shlex_quote
from io import StringIO as StringIO
from collections import UserDict as UserDict
from collections import UserList as UserList
from collections import UserString as UserString
from builtins import range as xrange
from builtins import zip as zip
from itertools import zip_longest as zip_longest
import builtins as builtins
import configparser as configparser
# import copyreg as copyreg
# import dbm.gnu as dbm_gnu
import _dummy_thread as _dummy_thread
import http.cookiejar as http_cookiejar
import http.cookies as http_cookies
import html.entities as html_entities
import html.parser as html_parser
import http.client as http_client
import email.mime.multipart as email_mime_multipart
import email.mime.nonmultipart as email_mime_nonmultipart
import email.mime.text as email_mime_text
import email.mime.base as email_mime_base
import http.server as BaseHTTPServer
import http.server as CGIHTTPServer
import http.server as SimpleHTTPServer
import pickle as cPickle
import queue as queue
# import reprlib as reprlib
import socketserver as socketserver
import _thread as _thread
import tkinter as tkinter
# import tkinter.dialog as tkinter_dialog
# import tkinter.filedialog as tkinter_filedialog
# import tkinter.scrolledtext as tkinter_scrolledtext
# import tkinter.simpledialog as tkinter_simpledialog
# import tkinter.tix as tkinter_tix
import tkinter.ttk as tkinter_ttk
import tkinter.constants as tkinter_constants
# import tkinter.dnd as tkinter_dnd
# import tkinter.colorchooser as tkinter_colorchooser
# import tkinter.commondialog as tkinter_commondialog
# import tkinter.filedialog as tkinter_tkfiledialog
# import tkinter.font as tkinter_font
# import tkinter.messagebox as tkinter_messagebox
# import tkinter.simpledialog as tkinter_tksimpledialog
import urllib.parse as urllib_parse
import urllib.error as urllib_error
import six.moves.urllib as urllib
import urllib.robotparser as urllib_robotparser
# import xmlrpc.client as xmlrpc_client
# import xmlrpc.server as xmlrpc_server

if sys.version_info >= (3, 4):
    from importlib import reload as reload_module
else:
    from imp import reload as reload_module
