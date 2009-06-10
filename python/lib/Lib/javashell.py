"""
Implement subshell functionality for Jython.

This is mostly to provide the environ object for the os module,
and subshell execution functionality for os.system and popen* functions.

javashell attempts to determine a suitable command shell for the host
operating system, and uses that shell to determine environment variables
and to provide subshell execution functionality.
"""
from java.lang import System, Runtime
from java.io import IOException
from java.io import InputStreamReader
from java.io import BufferedReader
from UserDict import UserDict
import jarray
import string
import sys
import types

# circular dependency to let javaos import javashell lazily
# without breaking LazyDict out into a new top-level module
from javaos import LazyDict 

__all__ = [ "shellexecute", "environ", "putenv", "getenv" ]

def __warn( *args ):
    print " ".join( [str( arg ) for arg in args ])
    
class _ShellEnv:
    """Provide environment derived by spawning a subshell and parsing its
    environment.  Also supports subshell execution functions and provides
    empty environment support for platforms with unknown shell functionality.
    """
    def __init__( self, cmd=None, getEnv=None, keyTransform=None ):
        """Construct _ShellEnv instance.
        cmd: list of exec() arguments required to run a command in
            subshell, or None
        getEnv: shell command to list environment variables, or None
        keyTransform: normalization function for environment keys,
          such as 'string.upper', or None
        """
        self.cmd = cmd
        self.getEnv = getEnv
        self.environment = LazyDict(populate=self._getEnvironment,
                                    keyTransform=keyTransform)
        self._keyTransform = self.environment._keyTransform

    def execute( self, cmd ):
        """Execute cmd in a shell, and return the java.lang.Process instance.
        Accepts either a string command to be executed in a shell,
        or a sequence of [executable, args...].
        """
        shellCmd = self._formatCmd( cmd )

        if self.environment._populated:
            env = self._formatEnvironment( self.environment )
        else:
            env = None
        try:
            p = Runtime.getRuntime().exec( shellCmd, env )
            return p
        except IOException, ex:
            raise OSError(
                0,
                "Failed to execute command (%s): %s" % ( shellCmd, ex )
                )

    ########## utility methods
    def _formatCmd( self, cmd ):
        """Format a command for execution in a shell."""
        if self.cmd is None:
            msgFmt = "Unable to execute commands in subshell because shell" \
                     " functionality not implemented for OS %s with shell"  \
                     " setting %s. Failed command=%s""" 
            raise OSError( 0, msgFmt % ( _osType, _envType, cmd ))
            
        if isinstance(cmd, basestring):
            shellCmd = self.cmd + [cmd]
        else:
            shellCmd = cmd
            
        return shellCmd

    def _formatEnvironment( self, env ):
        """Format enviroment in lines suitable for Runtime.exec"""
        lines = []
        for keyValue in env.items():
            lines.append( "%s=%s" % keyValue )
        return lines

    def _getEnvironment( self ):
        """Get the environment variables by spawning a subshell.
        This allows multi-line variables as long as subsequent lines do
        not have '=' signs.
        """
        env = {}
        if self.getEnv:
            try:
                p = self.execute( self.getEnv )
                r = BufferedReader( InputStreamReader( p.getInputStream() ) )
                lines = []
                while True:
                  line = r.readLine()
                  if not line:
                    break
                  lines.append(line)
                if '=' not in lines[0]:
                    __warn(
                        "Failed to get environment, getEnv command (%s) " \
                        "did not print environment as key=value lines.\n" \
                        "Output=%s" % ( self.getEnv, '\n'.join( lines ) )
                        )
                    return env

                for line in lines:
                    try:
                        i = line.index( '=' )
                        key = self._keyTransform(line[:i])
                        value = line[i+1:] # remove = and end-of-line
                    except ValueError:
                        # found no '=', treat line as part of previous value
                        value = '%s\n%s' % ( value, line )
                    env[ key ] = value
            except OSError, ex:
                __warn( "Failed to get environment, environ will be empty:",
                        ex )
        return env

def _getOsType( os=None ):
    """Select the OS behavior based on os argument, 'python.os' registry
    setting and 'os.name' Java property.
    os: explicitly select desired OS. os=None to autodetect, os='None' to
    disable 
    """
    os = str(os or sys.registry.getProperty( "python.os" ) or \
               System.getProperty( "os.name" ))
        
    _osTypeMap = (
        ( "nt", ( 'nt', 'Windows NT', 'Windows NT 4.0', 'WindowsNT',
                  'Windows 2000', 'Windows 2003', 'Windows XP', 'Windows CE',
                  'Windows Vista' )),
        ( "dos", ( 'dos', 'Windows 95', 'Windows 98', 'Windows ME' )),
        ( "mac", ( 'mac', 'MacOS', 'Darwin' )),
        ( "None", ( 'None', )),
        )
    foundType = None
    for osType, patterns in _osTypeMap:
        for pattern in patterns:
            if os.startswith( pattern ):
                foundType = osType
                break
        if foundType:
            break
    if not foundType:
        foundType = "posix" # default - posix seems to vary most widely

    return foundType

def _getShellEnv():
    # default to None/empty for shell and environment behavior
    shellCmd = None
    envCmd = None
    envTransform = None

    envType = sys.registry.getProperty("python.environment", "shell")
    if envType == "shell":
        osType = _getOsType()
        
        # override defaults based on osType
        if osType == "nt":
            shellCmd = ["cmd", "/c"]
            envCmd = "set"
            envTransform = string.upper
        elif osType == "dos":
            shellCmd = ["command.com", "/c"]
            envCmd = "set"
            envTransform = string.upper
        elif osType == "posix":
            shellCmd = ["sh", "-c"]
            envCmd = "env"
        elif osType == "mac":
            curdir = ':'  # override Posix directories
            pardir = '::' 
        elif osType == "None":
            pass
        # else:
        #    # may want a warning, but only at high verbosity:
        #    __warn( "Unknown os type '%s', using default behavior." % osType )

    return _ShellEnv( shellCmd, envCmd, envTransform )

_shellEnv = _getShellEnv()
shellexecute = _shellEnv.execute
