from _typeshed import Incomplete

class ServiceApiMixin:
    def create_service(
        self,
        task_template,
        name: Incomplete | None = None,
        labels: Incomplete | None = None,
        mode: Incomplete | None = None,
        update_config: Incomplete | None = None,
        networks: Incomplete | None = None,
        endpoint_config: Incomplete | None = None,
        endpoint_spec: Incomplete | None = None,
        rollback_config: Incomplete | None = None,
    ): ...
    def inspect_service(self, service, insert_defaults: Incomplete | None = None): ...
    def inspect_task(self, task): ...
    def remove_service(self, service): ...
    def services(self, filters: Incomplete | None = None, status: Incomplete | None = None): ...
    def service_logs(
        self,
        service,
        details: bool = False,
        follow: bool = False,
        stdout: bool = False,
        stderr: bool = False,
        since: int = 0,
        timestamps: bool = False,
        tail: str = "all",
        is_tty: Incomplete | None = None,
    ): ...
    def tasks(self, filters: Incomplete | None = None): ...
    def update_service(
        self,
        service,
        version,
        task_template: Incomplete | None = None,
        name: Incomplete | None = None,
        labels: Incomplete | None = None,
        mode: Incomplete | None = None,
        update_config: Incomplete | None = None,
        networks: Incomplete | None = None,
        endpoint_config: Incomplete | None = None,
        endpoint_spec: Incomplete | None = None,
        fetch_current_spec: bool = False,
        rollback_config: Incomplete | None = None,
    ): ...
