from _typeshed import Incomplete

class ExecApiMixin:
    def exec_create(
        self,
        container,
        cmd,
        stdout: bool = True,
        stderr: bool = True,
        stdin: bool = False,
        tty: bool = False,
        privileged: bool = False,
        user: str = "",
        environment: Incomplete | None = None,
        workdir: Incomplete | None = None,
        detach_keys: Incomplete | None = None,
    ): ...
    def exec_inspect(self, exec_id): ...
    def exec_resize(self, exec_id, height: Incomplete | None = None, width: Incomplete | None = None) -> None: ...
    def exec_start(
        self, exec_id, detach: bool = False, tty: bool = False, stream: bool = False, socket: bool = False, demux: bool = False
    ): ...
