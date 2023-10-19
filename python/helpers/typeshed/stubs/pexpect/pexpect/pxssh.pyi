from _typeshed import Incomplete

from pexpect import ExceptionPexpect, spawn

class ExceptionPxssh(ExceptionPexpect): ...

class pxssh(spawn):
    name: str
    UNIQUE_PROMPT: str
    PROMPT: Incomplete
    PROMPT_SET_SH: str
    PROMPT_SET_CSH: str
    SSH_OPTS: Incomplete
    force_password: bool
    debug_command_string: Incomplete
    options: Incomplete
    def __init__(
        self,
        timeout: int = 30,
        maxread: int = 2000,
        searchwindowsize: Incomplete | None = None,
        logfile: Incomplete | None = None,
        cwd: Incomplete | None = None,
        env: Incomplete | None = None,
        ignore_sighup: bool = True,
        echo: bool = True,
        options={},
        encoding: Incomplete | None = None,
        codec_errors: str = "strict",
        debug_command_string: bool = False,
        use_poll: bool = False,
    ) -> None: ...
    def levenshtein_distance(self, a, b): ...
    def try_read_prompt(self, timeout_multiplier): ...
    def sync_original_prompt(self, sync_multiplier: float = 1.0): ...
    def login(
        self,
        server,
        username: Incomplete | None = None,
        password: str = "",
        terminal_type: str = "ansi",
        original_prompt: str = "[#$]",
        login_timeout: int = 10,
        port: Incomplete | None = None,
        auto_prompt_reset: bool = True,
        ssh_key: Incomplete | None = None,
        quiet: bool = True,
        sync_multiplier: int = 1,
        check_local_ip: bool = True,
        password_regex: str = "(?i)(?:password:)|(?:passphrase for key)",
        ssh_tunnels={},
        spawn_local_ssh: bool = True,
        sync_original_prompt: bool = True,
        ssh_config: Incomplete | None = None,
        cmd: str = "ssh",
    ): ...
    def logout(self) -> None: ...
    def prompt(self, timeout: int = -1): ...
    def set_unique_prompt(self): ...
