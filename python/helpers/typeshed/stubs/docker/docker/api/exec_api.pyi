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
        environment=None,
        workdir=None,
        detach_keys=None,
    ): ...
    def exec_inspect(self, exec_id): ...
    def exec_resize(self, exec_id, height=None, width=None) -> None: ...
    def exec_start(
        self, exec_id, detach: bool = False, tty: bool = False, stream: bool = False, socket: bool = False, demux: bool = False
    ): ...
