from selenium.webdriver.remote.remote_connection import RemoteConnection as RemoteConnection

class SafariRemoteConnection(RemoteConnection):
    def __init__(self, remote_server_addr, keep_alive: bool = ...) -> None: ...
