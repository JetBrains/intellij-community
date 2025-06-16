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
        local_private_key_file=None,
        local_certificate_file=None,
        validate=...,
        version=None,
        ssl_options=None,
        ca_certs_file=None,
        valid_names=None,
        ca_certs_path=None,
        ca_certs_data=None,
        local_private_key_password=None,
        ciphers=None,
        sni=None,
    ) -> None: ...
    def wrap_socket(self, connection, do_handshake: bool = False) -> None: ...
    def start_tls(self, connection): ...

def check_hostname(sock, server_name, additional_names) -> None: ...
