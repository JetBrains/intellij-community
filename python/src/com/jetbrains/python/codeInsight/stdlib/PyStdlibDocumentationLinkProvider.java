package com.jetbrains.python.codeInsight.stdlib;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.PythonDocumentationLinkProvider;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.sdk.PythonSdkType;

import java.util.List;

/**
 * @author yole
 */
public class PyStdlibDocumentationLinkProvider implements PythonDocumentationLinkProvider {
  // use tools/stdlib-modindex.py to regenerate the map when new Python versions are released
  private static List<String> py2LibraryModules = ImmutableList.of(
    "abc",
    "aifc",
    "anydbm",
    "argparse",
    "base64",
    "BaseHTTPServer",
    "bdb",
    "binhex",
    "bisect",
    "cgi",
    "CGIHTTPServer",
    "cmath",
    "colorsys",
    "ConfigParser",
    "contextlib",
    "Cookie",
    "cookielib",
    "copy",
    "copy_reg",
    "ctypes",
    "curses.panel",
    "curses",
    "datetime",
    "dbhash",
    "dbm",
    "difflib",
    "doctest",
    "DocXMLRPCServer",
    "dumbdbm",
    "dummy_thread",
    "email.charset",
    "email.encoders",
    "email.errors",
    "email.generator",
    "email.header",
    "email.iterators",
    "email.message",
    "email.mime",
    "email.parser",
    "email",
    "email.utils",
    "fileinput",
    "ftplib",
    "functools",
    "future_builtins",
    "gdbm",
    "getpass",
    "gl",
    "gzip",
    "heapq",
    "htmllib",
    "httplib",
    "ic",
    "imaplib",
    "importlib",
    "io",
    "json",
    "logging",
    "MacOS",
    "mailcap",
    "mhlib",
    "MimeWriter",
    "msilib",
    "multiprocessing",
    "new",
    "numbers",
    "operator",
    "optparse",
    "os.path",
    "os",
    "pickle",
    "pickletools",
    "platform",
    "plistlib",
    "posix",
    "posixfile",
    "py_compile",
    "Queue",
    "readline",
    "rexec",
    "runpy",
    "sched",
    "ScrolledText",
    "sgmllib",
    "shelve",
    "SimpleXMLRPCServer",
    "smtpd",
    "socket",
    "sqlite3",
    "ssl",
    "statvfs",
    "string",
    "symtable",
    "sysconfig",
    "syslog",
    "tabnanny",
    "thread",
    "threading",
    "Tix",
    "Tkinter",
    "traceback",
    "ttk",
    "types",
    "unittest",
    "urllib",
    "urllib2",
    "urlparse",
    "warnings",
    "wave",
    "weakref",
    "whichdb",
    "wsgiref",
    "xmlrpclib",
    "_winreg",
    "__future__"
  );

  private static List<String> py3LibraryModules = ImmutableList.of(
    "abc",
    "aifc",
    "argparse",
    "array",
    "ast",
    "asynchat",
    "asyncore",
    "atexit",
    "audioop",
    "base64",
    "bdb",
    "binascii",
    "binhex",
    "bisect",
    "builtins",
    "bz2",
    "calendar",
    "cgi",
    "cgitb",
    "chunk",
    "cmath",
    "cmd",
    "code",
    "codecs",
    "codeop",
    "collections",
    "colorsys",
    "compileall",
    "concurrent.futures",
    "configparser",
    "contextlib",
    "copy",
    "copyreg",
    "crypt",
    "csv",
    "ctypes",
    "curses.ascii",
    "curses.panel",
    "curses",
    "datetime",
    "dbm",
    "decimal",
    "difflib",
    "dis",
    "distutils",
    "doctest",
    "dummy_threading",
    "email.charset",
    "email.encoders",
    "email.errors",
    "email.generator",
    "email.header",
    "email.iterators",
    "email.message",
    "email.mime",
    "email.parser",
    "email",
    "email.utils",
    "errno",
    "fcntl",
    "filecmp",
    "fileinput",
    "fnmatch",
    "formatter",
    "fpectl",
    "fractions",
    "ftplib",
    "functools",
    "gc",
    "getopt",
    "getpass",
    "gettext",
    "glob",
    "grp",
    "gzip",
    "hashlib",
    "heapq",
    "hmac",
    "html.entities",
    "html.parser",
    "html",
    "http.client",
    "http.cookiejar",
    "http.cookies",
    "http.server",
    "imaplib",
    "imghdr",
    "imp",
    "importlib",
    "inspect",
    "io",
    "itertools",
    "json",
    "keyword",
    "linecache",
    "locale",
    "logging.config",
    "logging.handlers",
    "logging",
    "macpath",
    "mailbox",
    "mailcap",
    "marshal",
    "math",
    "mimetypes",
    "mmap",
    "modulefinder",
    "msilib",
    "msvcrt",
    "multiprocessing",
    "numbers",
    "operator",
    "optparse",
    "os.path",
    "os",
    "ossaudiodev",
    "parser",
    "pickle",
    "pickletools",
    "pipes",
    "pkgutil",
    "platform",
    "plistlib",
    "poplib",
    "posix",
    "pprint",
    "pty",
    "pwd",
    "pyclbr",
    "pydoc",
    "xml.parsers.expat",
    "py_compile",
    "queue",
    "quopri",
    "random",
    "re",
    "readline",
    "reprlib",
    "resource",
    "rlcompleter",
    "runpy",
    "sched",
    "select",
    "shelve",
    "shlex",
    "shutil",
    "signal",
    "site",
    "smtpd",
    "smtplib",
    "sndhdr",
    "socket",
    "socketserver",
    "spwd",
    "sqlite3",
    "ssl",
    "stat",
    "string",
    "stringprep",
    "struct",
    "subprocess",
    "sunau",
    "symbol",
    "symtable",
    "sys",
    "sysconfig",
    "syslog",
    "tabnanny",
    "tarfile",
    "telnetlib",
    "tempfile",
    "termios",
    "test",
    "textwrap",
    "threading",
    "time",
    "timeit",
    "tkinter",
    "tkinter.scrolledtext",
    "tkinter.tix",
    "tkinter.ttk",
    "token",
    "tokenize",
    "trace",
    "traceback",
    "tty",
    "types",
    "unicodedata",
    "unittest",
    "urllib.error",
    "urllib.parse",
    "urllib.request",
    "urllib.robotparser",
    "uu",
    "uuid",
    "warnings",
    "wave",
    "weakref",
    "webbrowser",
    "winreg",
    "winsound",
    "wsgiref",
    "xdrlib",
    "xml.dom.minidom",
    "xml.dom.pulldom",
    "xml.dom",
    "xml.etree.ElementTree",
    "xml.sax.handler",
    "xml.sax.xmlreader",
    "xml.sax",
    "xml.sax.saxutils",
    "xmlrpc.client",
    "xmlrpc.server",
    "zipfile",
    "zipimport",
    "zlib",
    "_dummy_thread",
    "_thread",
    "__future__"
  );

  @Override
  public String getExternalDocumentationUrl(PsiElement element, PsiElement originalElement) {
    PsiFileSystemItem file = element instanceof PsiFileSystemItem ? (PsiFileSystemItem) element : element.getContainingFile();
    if (PyNames.INIT_DOT_PY.equals(file.getName())) {
      file = file.getParent();
      assert file != null;
    }
    Sdk sdk = PyBuiltinCache.findSdkForFile(file);
    VirtualFile vFile = file.getVirtualFile();
    if (vFile != null && sdk != null && PythonSdkType.isStdLib(vFile, sdk)) {
      PyQualifiedName qName = ResolveImportUtil.findCanonicalImportPath(element, originalElement);
      return getStdlibUrlFor(element, qName, sdk);
    }
    return null;
  }

  @Override
  public String getExternalDocumentationRoot(Sdk sdk) {
    final String versionString = sdk.getVersionString();
    if (versionString != null && StringUtil.startsWithIgnoreCase(versionString, "jython")) {
      return "http://jython.org/docs/library/";
    }
    final String pyVersion = PythonDocumentationProvider.pyVersion(versionString);
    StringBuilder urlBuilder = new StringBuilder("http://docs.python.org/");
    if (pyVersion != null) {
      urlBuilder.append(pyVersion).append("/");
    }
    if (pyVersion != null && (pyVersion.startsWith("2.4") || pyVersion.startsWith("2.5"))) {
      urlBuilder.append("lib/");
    }
    else {
      urlBuilder.append("library/");
    }
    return urlBuilder.toString();
  }

  private String getStdlibUrlFor(PsiElement element, PyQualifiedName moduleName, Sdk sdk) {
    StringBuilder urlBuilder = new StringBuilder(getExternalDocumentationRoot(sdk));
    String qnameString = moduleName.toString();
    if (qnameString.equals("ntpath") || qnameString.equals("posixpath")) {
      qnameString = "os.path";
    }
    else if (qnameString.equals("cPickle")) {
      qnameString = "pickle";
    }
    else if (qnameString.equals("pyexpat")) {
      qnameString = "xml.parsers.expat";
    }

    final String pyVersion = PythonDocumentationProvider.pyVersion(sdk.getVersionString());
    List<String> modules = pyVersion != null && pyVersion.startsWith("3") ? py3LibraryModules : py2LibraryModules;
    boolean foundModule = false;
    for (String module : modules) {
      if (qnameString.startsWith(module)) {
        urlBuilder.append(module);
        urlBuilder.append(".html");
        foundModule = true;
        break;
      }
    }
    if (foundModule && element instanceof PsiNamedElement && !(element instanceof PyFile)) {
      urlBuilder.append('#').append(moduleName).append(".");
      if (element instanceof PyFunction) {
        final PyClass containingClass = ((PyFunction)element).getContainingClass();
        if (containingClass != null) {
          urlBuilder.append(containingClass.getName()).append('.');
        }
      }
      urlBuilder.append(((PsiNamedElement)element).getName());
    }
    return urlBuilder.toString();
  }

}
