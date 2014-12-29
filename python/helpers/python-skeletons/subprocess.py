"""Skeleton for 'subprocess' stdlib module."""


def call(args, bufsize=0, executable=None, stdin=None, stdout=None, stderr=None,
         preexec_fn=None, close_fds=False, shell=False, cwd=None, env=None,
         universal_newlines=False, startupinfo=None, creationflags=0,
         timeout=None, restore_signals=True, start_new_session=False,
         pass_fds=()):
    """Run the command described by args.

    :type args: collections.Iterable[bytes | unicode]
    :type bufsize: int
    :type executable: bytes | unicode | None
    :type close_fds: bool
    :type shell: bool
    :type cwd: bytes | unicode | None
    :type env: collections.Mapping | None
    :type universal_newlines: bool
    :type creationflags: int
    :rtype: int
    """
    return 0


def check_call(args, bufsize=0, executable=None, stdin=None, stdout=None,
               stderr=None, preexec_fn=None, close_fds=False, shell=False,
               cwd=None, env=None, universal_newlines=False, startupinfo=None,
               creationflags=0, timeout=None, restore_signals=True,
               start_new_session=False, pass_fds=()):
    """Run command with arguments. Wait for command to complete. If the return
    code was zero then return, otherwise raise CalledProcessError.

    :type args: collections.Iterable[bytes | unicode]
    :type bufsize: int
    :type executable: bytes | unicode | None
    :type close_fds: bool
    :type shell: bool
    :type cwd: bytes | unicode | None
    :type env: collections.Mapping | None
    :type universal_newlines: bool
    :type creationflags: int
    :rtype: int
    """
    return 0


def check_output(args, bufsize=0, executable=None, stdin=None, stderr=None,
                 preexec_fn=None, close_fds=False, shell=False, cwd=None,
                 env=None, universal_newlines=False, startupinfo=None,
                 creationflags=0, timeout=None, restore_signals=True,
                 start_new_session=False, pass_fds=()):
    """Run command with arguments and return its output as a byte string.

    :type args: collections.Iterable[bytes | unicode]
    :type bufsize: int
    :type executable: bytes | unicode | None
    :type close_fds: bool
    :type shell: bool
    :type cwd: bytes | unicode | None
    :type env: collections.Mapping | None
    :type universal_newlines: bool
    :type creationflags: int
    :rtype: bytes
    """
    pass


class Popen(object):
    """Execute a child program in a new process.

    :type returncode: int
    """

    def __init__(self, args, bufsize=0, executable=None, stdin=None,
                 stdout=None, stderr=None, preexec_fn=None, close_fds=False,
                 shell=False, cwd=None, env=None, universal_newlines=False,
                 startupinfo=None, creationflags=0, timeout=None,
                 restore_signals=True, start_new_session=False, pass_fds=()):
        """Popen constructor.

        :type args: collections.Iterable[bytes | unicode]
        :type bufsize: int
        :type executable: bytes | unicode | None
        :type close_fds: bool
        :type shell: bool
        :type cwd: bytes | unicode | None
        :type env: collections.Mapping | None
        :type universal_newlines: bool
        :type creationflags: int
        """
        self.stdin = stdin
        self.stdout = stdout
        self.stderr = stderr
        self.pid = 0
        self.returncode = 0

    def poll(self):
        """Check if child process has terminated.

        :rtype: int
        """
        return 0

    def wait(self, timeout=None):
        """Wait for child process to terminate.

        :rtype: int
        """
        return 0

    def communicate(self, input=None, timeout=None):
        """Interact with process: Send data to stdin. Read data from stdout and
        stderr, until end-of-file is reached.

        :type input: bytes | unicode | None
        :rtype: (bytes, bytes)
        """
        return b'', b''

    def send_signal(self, signal):
        """Sends the signal signal to the child.

        :type signal: int
        :rtype: None
        """
        pass

    def terminate(self):
        """Stop the child.

        :rtype: None
        """
        pass

    def kill(self):
        """Kills the child.

        :rtype: None
        """
        pass
