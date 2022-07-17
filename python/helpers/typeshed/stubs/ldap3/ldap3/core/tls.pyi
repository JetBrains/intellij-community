from typing import Any

use_ssl_context: bool

class Tls:
    ssl_options: Any
    validate: Any
    ca_certs_file: Any
    ca_certs_path: Any
    ca_certs_data: Any
    private_key_password: Any
    version: Any
    private_key_file: Any
    certificate_file: Any
    valid_names: Any
    ciphers: Any
    sni: Any
    def __init__(
        self,
        local_private_key_file: Any | None = ...,
        local_certificate_file: Any | None = ...,
        validate=...,
        version: Any | None = ...,
        ssl_options: Any | None = ...,
        ca_certs_file: Any | None = ...,
        valid_names: Any | None = ...,
        ca_certs_path: Any | None = ...,
        ca_certs_data: Any | None = ...,
        local_private_key_password: Any | None = ...,
        ciphers: Any | None = ...,
        sni: Any | None = ...,
    ) -> None: ...
    def wrap_socket(self, connection, do_handshake: bool = ...) -> None: ...
    def start_tls(self, connection): ...

def check_hostname(sock, server_name, additional_names) -> None: ...
