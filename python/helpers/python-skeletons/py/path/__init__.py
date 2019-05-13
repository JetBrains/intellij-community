""" unified file system api """


# py.path.common
class NeverRaised(Exception):
    pass

class PathBase(object):
    """ shared implementation for filesystem path objects."""

    def basename(self):
        """ basename part of path. """

    def dirname(self):
        """ dirname part of path. """

    def purebasename(self):
        """ pure base name of the path."""

    def ext(self):
        """ extension of the path (including the '.')."""

    def dirpath(self, *args, **kwargs):
        """ return the directory path joined with any given path arguments.  """

    def read_binary(self):
        """ read and return a bytestring from reading the path. """

    def read_text(self, encoding):
        """ read and return a Unicode string from reading the path. """


    def read(self, mode='r'):
        """ read and return a bytestring from reading the path. """

    def readlines(self, cr=1):
        """ read and return a list of lines from the path. if cr is False, the 
            newline will be removed from the end of each line. """

    def load(self):
        """ (deprecated) return object unpickled from self.read() """

    def move(self, target):
        """ move this path to target. """

    def __repr__(self):
        """ return a string representation of this path. """

    def check(self, **kw):
        """ check a path for existence and properties.

            Without arguments, return True if the path exists, otherwise False.

            valid checkers::

                file=1    # is a file
                file=0    # is not a file (may not even exist)
                dir=1     # is a dir
                link=1    # is a link
                exists=1  # exists

            You can specify multiple checker definitions, for example::

                path.check(file=1, link=1)  # a link pointing to a file
        """

    def fnmatch(self, pattern):
        """return true if the basename/fullname matches the glob-'pattern'.

        valid pattern characters::

            *       matches everything
            ?       matches any single character
            [seq]   matches any character in seq
            [!seq]  matches any char not in seq

        If the pattern contains a path-separator then the full path
        is used for pattern matching and a '*' is prepended to the
        pattern.

        if the pattern doesn't contain a path-separator the pattern
        is only matched against the basename.
        """

    def relto(self, relpath):
        """ return a string which is the relative part of the path
            to the given 'relpath'.
        """

    def ensure_dir(self, *args):
        """ ensure the path joined with args is a directory. """

    def bestrelpath(self, dest):
        """ return a string which is a relative path from self
            (assumed to be a directory) to dest such that
            self.join(bestrelpath) == dest and if not such
            path can be determined return dest.
        """

    def exists(self):
        """ check a path for existence """

    def isdir(self):
        """ check a directory for existence. """

    def isfile(self):
        """ check a file for existence. """

    def parts(self, reverse=False):
        """ return a root-first list of all ancestor directories
            plus the path itself.
        """

    def common(self, other):
        """ return the common part shared with the other path
            or None if there is no common part.
        """

    def visit(self, fil=None, rec=None, ignore=NeverRaised, bf=False, sort=False):
        """ yields all paths below the current one

            fil is a filter (glob pattern or callable), if not matching the
            path will not be yielded, defaulting to None (everything is
            returned)

            rec is a filter (glob pattern or callable) that controls whether
            a node is descended, defaulting to None

            ignore is an Exception class that is ignoredwhen calling dirlist()
            on any of the paths (by default, all exceptions are reported)

            bf if True will cause a breadthfirst search instead of the
            default depthfirst. Default: False

            sort if True will sort entries within each directory level.
        """

    def samefile(self, other):
        """ return True if other refers to the same stat object as self. """


# py.path.local
class PosixPath(PathBase):
    def chown(self, user, group, rec=0):
        """ change ownership to the given user and group.
            user and group may be specified by a number or
            by a name.  if rec is True change ownership
            recursively.
        """

    def readlink(self):
        """ return value of a symbolic link. """

    def mklinkto(self, oldname):
        """ posix style hard link to another name. """

    def mksymlinkto(self, value, absolute=1):
        """ create a symbolic link with the given value (pointing to another name). """


class LocalPath(PosixPath):
    """ object oriented interface to os.path and other local filesystem
        related information.
    """
    class ImportMismatchError(ImportError):
        """ raised on pyimport() if there is a mismatch of __file__'s"""

    def __init__(self, path=None, expanduser=False):
        """ Initialize and return a local Path instance.

        Path can be relative to the current directory.
        If path is None it defaults to the current working directory.
        If expanduser is True, tilde-expansion is performed.
        Note that Path instances always carry an absolute path.
        Note also that passing in a local path object will simply return
        the exact same path object. Use new() to get a new copy.
        """

    def samefile(self, other):
        """ return True if 'other' references the same file as 'self'.
        """

    def remove(self, rec=1, ignore_errors=False):
        """ remove a file or directory (or a directory tree if rec=1).
        if ignore_errors is True, errors while removing directories will
        be ignored.
        """

    def computehash(self, hashtype="md5", chunksize=524288):
        """ return hexdigest of hashvalue for this file. """

    def new(self, **kw):
        """ create a modified version of this path.
            the following keyword arguments modify various path parts::

              a:/some/path/to/a/file.ext
              xx                           drive
              xxxxxxxxxxxxxxxxx            dirname
                                xxxxxxxx   basename
                                xxxx       purebasename
                                     xxx   ext
        """

    def dirpath(self, *args, **kwargs):
        """ return the directory path joined with any given path arguments.  """

    def join(self, *args, **kwargs):
        """ return a new path by appending all 'args' as path
        components.  if abs=1 is used restart from root if any
        of the args is an absolute path.
        """

    def open(self, mode='r', ensure=False, encoding=None):
        """ return an opened file with the given mode.

        If ensure is True, create parent directories if needed.
        """

    def islink(self):
        pass

    def check(self, **kw):
        pass

    def listdir(self, fil=None, sort=None):
        """ list directory contents, possibly filter by the given fil func
            and possibly sorted.
        """

    def size(self):
        """ return size of the underlying file object """

    def mtime(self):
        """ return last modification time of the path. """

    def copy(self, target, mode=False, stat=False):
        """ copy path to target.

            If mode is True, will copy copy permission from path to target.
            If stat is True, copy permission, last modification
            time, last access time, and flags from path to target.
        """

    def rename(self, target):
        """ rename this path to target. """

    def dump(self, obj, bin=1):
        """ pickle object into path location"""

    def mkdir(self, *args):
        """ create & return the directory joined with args. """

    def write_binary(self, data, ensure=False):
        """ write binary data into path.   If ensure is True create
        missing parent directories.
        """

    def write_text(self, data, encoding, ensure=False):
        """ write text data into path using the specified encoding.
        If ensure is True create missing parent directories.
        """

    def write(self, data, mode='w', ensure=False):
        """ write data into path.   If ensure is True create
        missing parent directories.
        """

    def ensure(self, *args, **kwargs):
        """ ensure that an args-joined path exists (by default as
            a file). if you specify a keyword argument 'dir=True'
            then the path is forced to be a directory path.
        """

    def stat(self, raising=True):
        """ Return an os.stat() tuple. """

    def lstat(self):
        """ Return an os.lstat() tuple. """

    def setmtime(self, mtime=None):
        """ set modification time for the given path.  if 'mtime' is None
        (the default) then the file's mtime is set to current time.

        Note that the resolution for 'mtime' is platform dependent.
        """

    def chdir(self):
        """ change directory to self and return old current directory """

    def realpath(self):
        """ return a new path which contains no symbolic links."""

    def atime(self):
        """ return last access time of the path. """

    def chmod(self, mode, rec=0):
        """ change permissions to the given mode. If mode is an
            integer it directly encodes the os-specific modes.
            if rec is True perform recursively.
        """

    def pypkgpath(self):
        """ return the Python package path by looking for the last
        directory upwards which still contains an __init__.py.
        Return None if a pkgpath can not be determined.
        """

    def pyimport(self, modname=None, ensuresyspath=True):
        """ return path as an imported python module.

        If modname is None, look for the containing package
        and construct an according module name.
        The module will be put/looked up in sys.modules.
        if ensuresyspath is True then the root dir for importing
        the file (taking __init__.py files into account) will
        be prepended to sys.path if it isn't there already.
        If ensuresyspath=="append" the root dir will be appended
        if it isn't already contained in sys.path.
        if ensuresyspath is False no modification of syspath happens.
        """

    def sysexec(self, *argv, **popen_opts):
        """ return stdout text from executing a system child process,
            where the 'self' path points to executable.
            The process is directly invoked and not through a system shell.
        """

    def sysfind(cls, name, checker=None, paths=None):
        """ return a path object found by looking at the systems
            underlying PATH specification. If the checker is not None
            it will be invoked to filter matching paths.  If a binary
            cannot be found, None is returned
            Note: This is probably not working on plain win32 systems
            but may work on cygwin.
        """

    def get_temproot(cls):
        """ return the system's temporary directory
            (where tempfiles are usually created in)
        """

    def mkdtemp(cls, rootdir=None):
        """ return a Path object pointing to a fresh new temporary directory
            (which we created ourself).
        """

    def make_numbered_dir(cls, prefix='session-', rootdir=None, keep=3,
                          lock_timeout = 172800):   # two days
        """ return unique directory with a number greater than the current
            maximum one.  The number is assumed to start directly after prefix.
            if keep is true directories with a number less than (maxnum-keep)
            will be removed.
        """

local = LocalPath


# py.path.cacheutil

"""
This module contains multithread-safe cache implementations.

All Caches have

    getorbuild(key, builder)
    delentry(key)

methods and allow configuration when instantiating the cache class.
"""

class BasicCache(object):
    """ BasicCache class.
    """ 


class BuildcostAccessCache(BasicCache):
    """ A BuildTime/Access-counting cache implementation.
        the weight of a value is computed as the product of

            num-accesses-of-a-value * time-to-build-the-value

        The values with the least such weights are evicted
        if the cache maxentries threshold is superceded.
        For implementation flexibility more than one object
        might be evicted at a time.
    """


class AgingCache(BasicCache):
    """ This cache prunes out cache entries that are too old.
    """


# py.path.svnwc

class SvnPathBase(PathBase):
    """ Base implementation for SvnPath implementations. """

    def new(self, **kw):
        """ create a modified version of this path. A 'rev' argument
            indicates a new revision.
            the following keyword arguments modify various path parts::

              http://host.com/repo/path/file.ext
              |-----------------------|          dirname
                                        |------| basename
                                        |--|     purebasename
                                            |--| ext
        """

    def join(self, *args):
        """ return a new Path (with the same revision) which is composed
            of the self Path followed by 'args' path components.
        """

    def propget(self, name):
        """ return the content of the given property. """

    def proplist(self):
        """ list all property names. """

    def size(self):
        """ Return the size of the file content of the Path. """

    def mtime(self):
        """ Return the last modification time of the file. """


class SvnWCCommandPath(PathBase):
    """ path implementation offering access/modification to svn working copies.
        It has methods similar to the functions in os.path and similar to the
        commands of the svn client.
    """

    def dump(self, obj):
        """ pickle object into path location"""

    def svnurl(self):
        """ return current SvnPath for this WC-item. """

    def switch(self, url):
        """ switch to given URL. """

    def checkout(self, url=None, rev=None):
        """ checkout from url to local wcpath. """

    def update(self, rev='HEAD', interactive=True):
        """ update working copy item to given revision. (None -> HEAD). """

    def write(self, content, mode='w'):
        """ write content into local filesystem wc. """

    def dirpath(self, *args):
        """ return the directory Path of the current Path. """

    def ensure(self, *args, **kwargs):
        """ ensure that an args-joined path exists (by default as
            a file). if you specify a keyword argument 'directory=True'
            then the path is forced  to be a directory path.
        """

    def mkdir(self, *args):
        """ create & return the directory joined with args. """

    def add(self):
        """ add ourself to svn """

    def remove(self, rec=1, force=1):
        """ remove a file or a directory tree. 'rec'ursive is
            ignored and considered always true (because of
            underlying svn semantics.
        """

    def copy(self, target):
        """ copy path to target."""

    def rename(self, target):
        """ rename this path to target. """

    def lock(self):
        """ set a lock (exclusive) on the resource """

    def unlock(self):
        """ unset a previously set lock """

    def cleanup(self):
        """ remove any locks from the resource """

    def status(self, updates=0, rec=0, externals=0):
        """ return (collective) Status object for this file. """

    def diff(self, rev=None):
        """ return a diff of the current path against revision rev (defaulting
            to the last one).
        """

    def blame(self):
        """ return a list of tuples of three elements:
            (revision, commiter, line)
        """

    def commit(self, msg='', rec=1):
        """ commit with support for non-recursive commits """

    def propset(self, name, value, *args):
        """ set property name to value on this path. """

    def propget(self, name):
        """ get property name on this path. """

    def propdel(self, name):
        """ delete property name on this path. """

    def proplist(self, rec=0):
        """ return a mapping of property names to property values.
            If rec is True, then return a dictionary mapping sub-paths to such mappings.
        """

    def revert(self, rec=0):
        """ revert the local changes of this path. if rec is True, do so
            recursively. """

    def new(self, **kw):
        """ create a modified version of this path. A 'rev' argument
            indicates a new revision.
            the following keyword arguments modify various path parts:

              http://host.com/repo/path/file.ext
              |-----------------------|          dirname
                                        |------| basename
                                        |--|     purebasename
                                            |--| ext
        """

    def join(self, *args, **kwargs):
        """ return a new Path (with the same revision) which is composed
            of the self Path followed by 'args' path components.
        """

    def info(self, usecache=1):
        """ return an Info structure with svn-provided information. """

    def listdir(self, fil=None, sort=None):
        """ return a sequence of Paths.

        listdir will return either a tuple or a list of paths
        depending on implementation choices.
        """

    def open(self, mode='r'):
        """ return an opened file with the given mode. """

    def log(self, rev_start=None, rev_end=1, verbose=False):
        """ return a list of LogEntry instances for this path.
            rev_start is the starting revision (defaulting to the first one).
            rev_end is the last revision (defaulting to HEAD).
            if verbose is True, then the LogEntry instances also know which files changed.
        """


class SvnAuth(object):
    """ container for auth information for Subversion """


svnwc = SvnWCCommandPath


# py.path.svnurl

class SvnCommandPath(SvnPathBase):
    """ path implementation that offers access to (possibly remote) subversion
    repositories. """


    def open(self, mode='r'):
        """ return an opened file with the given mode. """

    def dirpath(self, *args, **kwargs):
        """ return the directory path of the current path joined
            with any given path arguments.
        """

    # modifying methods (cache must be invalidated)
    def mkdir(self, *args, **kwargs):
        """ create & return the directory joined with args.
        pass a 'msg' keyword argument to set the commit message.
        """

    def copy(self, target, msg='copied by py lib invocation'):
        """ copy path to target with checkin message msg."""

    def rename(self, target, msg="renamed by py lib invocation"):
        """ rename this path to target with checkin message msg. """

    def remove(self, rec=1, msg='removed by py lib invocation'):
        """ remove a file or directory (or a directory tree if rec=1) with
            checkin message msg."""

    def export(self, topath):
        """ export to a local path

            topath should not exist prior to calling this, returns a
            py.path.local instance
        """

    def ensure(self, *args, **kwargs):
        """ ensure that an args-joined path exists (by default as
            a file). If you specify a keyword argument 'dir=True'
            then the path is forced to be a directory path.
        """

    # end of modifying methods
 
    def info(self):
        """ return an Info structure with svn-provided information. """
 
    def listdir(self, fil=None, sort=None):
        """ list directory contents, possibly filter by the given fil func
            and possibly sorted.
        """
 
    def log(self, rev_start=None, rev_end=1, verbose=False):
        """ return a list of LogEntry instances for this path.
            rev_start is the starting revision (defaulting to the first one).
            rev_end is the last revision (defaulting to HEAD).
            if verbose is True, then the LogEntry instances also know which files changed.
        """

svnurl = SvnCommandPath
