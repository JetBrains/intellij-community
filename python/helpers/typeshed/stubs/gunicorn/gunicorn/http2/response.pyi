import socket

from gunicorn.config import Config
from gunicorn.http import Request
from gunicorn.http.wsgi import Response
from gunicorn.http2.connection import HTTP2ServerConnection

class HTTP2Response(Response):
    h2_conn: HTTP2ServerConnection
    stream_id: int
    def __init__(
        self, req: Request, sock: socket.socket, cfg: Config, h2_conn: HTTP2ServerConnection, stream_id: int
    ) -> None: ...
    def is_chunked(self) -> bool: ...
    def can_sendfile(self) -> bool: ...
    def send_headers(self) -> None: ...
    def close(self) -> None: ...
