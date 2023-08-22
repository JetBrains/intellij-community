from collections.abc import Iterable, Mapping
from typing import NoReturn

from paramiko.channel import Channel, ChannelFile, ChannelStderrFile, ChannelStdinFile
from paramiko.hostkeys import HostKeys
from paramiko.pkey import PKey
from paramiko.sftp_client import SFTPClient
from paramiko.transport import Transport
from paramiko.util import ClosingContextManager

from .transport import _SocketLike

class SSHClient(ClosingContextManager):
    def __init__(self) -> None: ...
    def load_system_host_keys(self, filename: str | None = ...) -> None: ...
    def load_host_keys(self, filename: str) -> None: ...
    def save_host_keys(self, filename: str) -> None: ...
    def get_host_keys(self) -> HostKeys: ...
    def set_log_channel(self, name: str) -> None: ...
    def set_missing_host_key_policy(self, policy: type[MissingHostKeyPolicy] | MissingHostKeyPolicy) -> None: ...
    def connect(
        self,
        hostname: str,
        port: int = ...,
        username: str | None = ...,
        password: str | None = ...,
        pkey: PKey | None = ...,
        key_filename: str | None = ...,
        timeout: float | None = ...,
        allow_agent: bool = ...,
        look_for_keys: bool = ...,
        compress: bool = ...,
        sock: _SocketLike | None = ...,
        gss_auth: bool = ...,
        gss_kex: bool = ...,
        gss_deleg_creds: bool = ...,
        gss_host: str | None = ...,
        banner_timeout: float | None = ...,
        auth_timeout: float | None = ...,
        gss_trust_dns: bool = ...,
        passphrase: str | None = ...,
        disabled_algorithms: dict[str, Iterable[str]] | None = ...,
    ) -> None: ...
    def close(self) -> None: ...
    def exec_command(
        self,
        command: str,
        bufsize: int = ...,
        timeout: float | None = ...,
        get_pty: bool = ...,
        environment: dict[str, str] | None = ...,
    ) -> tuple[ChannelStdinFile, ChannelFile, ChannelStderrFile]: ...
    def invoke_shell(
        self,
        term: str = ...,
        width: int = ...,
        height: int = ...,
        width_pixels: int = ...,
        height_pixels: int = ...,
        environment: Mapping[str, str] | None = ...,
    ) -> Channel: ...
    def open_sftp(self) -> SFTPClient: ...
    def get_transport(self) -> Transport | None: ...

class MissingHostKeyPolicy:
    def missing_host_key(self, client: SSHClient, hostname: str, key: PKey) -> None: ...

class AutoAddPolicy(MissingHostKeyPolicy):
    def missing_host_key(self, client: SSHClient, hostname: str, key: PKey) -> None: ...

class RejectPolicy(MissingHostKeyPolicy):
    def missing_host_key(self, client: SSHClient, hostname: str, key: PKey) -> NoReturn: ...

class WarningPolicy(MissingHostKeyPolicy):
    def missing_host_key(self, client: SSHClient, hostname: str, key: PKey) -> None: ...
