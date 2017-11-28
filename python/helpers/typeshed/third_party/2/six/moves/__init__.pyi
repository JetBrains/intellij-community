# Stubs for six.moves
#
# Note: Commented out items means they weren't implemented at the time.
# Uncomment them when the modules have been added to the typeshed.
from cStringIO import StringIO as cStringIO
from itertools import ifilter as filter
from itertools import ifilterfalse as filterfalse
from __builtin__ import raw_input as input
from __builtin__ import intern as intern
from itertools import imap as map
from os import getcwdu as getcwd
from os import getcwd as getcwdb
from __builtin__ import xrange as range
from __builtin__ import reload as reload_module
from __builtin__ import reduce as reduce
from pipes import quote as shlex_quote
from StringIO import StringIO as StringIO
from UserDict import UserDict as UserDict
from UserList import UserList as UserList
from UserString import UserString as UserString
from __builtin__ import xrange as xrange
from itertools import izip as zip
from itertools import izip_longest as zip_longest
import __builtin__ as builtins
import ConfigParser as configparser
# import copy_reg as copyreg
# import gdbm as dbm_gnu
# import dummy_thread as _dummy_thread
import cookielib as http_cookiejar
import Cookie as http_cookies
import htmlentitydefs as html_entities
import HTMLParser as html_parser
import httplib as http_client
# import email.MIMEMultipart as email_mime_multipart
# import email.MIMENonMultipart as email_mime_nonmultipart
import email.MIMEText as email_mime_text
# import email.MIMEBase as email_mime_base
import BaseHTTPServer as BaseHTTPServer
# import CGIHTTPServer as CGIHTTPServer
# import SimpleHTTPServer as SimpleHTTPServer
import cPickle as cPickle
import Queue as queue
import repr as reprlib
import SocketServer as socketserver
import thread as _thread
# import Tkinter as tkinter
# import Dialog as tkinter_dialog
# import FileDialog as tkinter_filedialog
# import ScrolledText as tkinter_scrolledtext
# import SimpleDialog as tkinter_simpledialog
# import Tix as tkinter_tix
# import ttk as tkinter_ttk
# import Tkconstants as tkinter_constants
# import Tkdnd as tkinter_dnd
# import tkColorChooser as tkinter_colorchooser
# import tkCommonDialog as tkinter_commondialog
# import tkFileDialog as tkinter_tkfiledialog
# import tkFont as tkinter_font
# import tkMessageBox as tkinter_messagebox
# import tkSimpleDialog as tkinter_tksimpledialog
import six.moves.urllib.parse as urllib_parse
import six.moves.urllib.error as urllib_error
import six.moves.urllib as urllib
import robotparser as urllib_robotparser
# import xmlrpclib as xmlrpc_client
# import SimpleXMLRPCServer as xmlrpc_server
