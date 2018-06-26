import socket
import threading

from pydev_console.thrift_transport import TSyncClient, open_transports_as_client, _create_client_server_transports
from _jetbrains_thriftpy.protocol import TBinaryProtocolFactory
from _jetbrains_thriftpy.server import TThreadedServer
from _jetbrains_thriftpy.thrift import TProcessor


def make_rpc_client(client_service, host, port, proto_factory=TBinaryProtocolFactory()):
    client_transport, server_transport = open_transports_as_client((host, port))

    client_protocol = proto_factory.get_protocol(client_transport)

    client = TSyncClient(client_service, client_protocol)

    return client, server_transport


def start_rpc_server(server_transport, server_service, server_handler, proto_factory=TBinaryProtocolFactory(strict_read=False,
                                                                                                            strict_write=False)):
    # setup server
    processor = TProcessor(server_service, server_handler)

    # todo as `server.serve()` is excessive we may want to get rid of `server` as `TThreadedServer`
    server = TThreadedServer(processor, server_transport, iprot_factory=proto_factory)

    client = server.trans.accept()
    t = threading.Thread(target=server.handle, args=(client,))
    # t.setDaemon(self.daemon)
    t.start()

    return server


def start_rpc_server_and_make_client(host, port, server_service, client_service, server_handler,
                                     proto_factory=TBinaryProtocolFactory(strict_read=False, strict_write=False)):
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    server_socket.bind((host, port))
    server_socket.listen(1)

    t = threading.Thread(target=_rpc_server, args=(server_socket, server_service, client_service, server_handler, proto_factory))
    # t.setDaemon(self.daemon)
    t.start()

    return server_socket


def _rpc_server(server_socket, server_service, client_service, server_handler, proto_factory):
    client_socket, address = server_socket.accept()

    client_transport, server_transport = _create_client_server_transports(client_socket)

    # setup server
    processor = TProcessor(server_service, server_handler)

    # todo as `server.serve()` is excessive we may want to get rid of `server` as `TThreadedServer`
    server = TThreadedServer(processor, server_transport, iprot_factory=proto_factory)

    client = server.trans.accept()
    t = threading.Thread(target=server.handle, args=(client,))
    # t.setDaemon(self.daemon)
    t.start()

    client_protocol = proto_factory.get_protocol(client_transport)

    client = TSyncClient(client_service, client_protocol)

    # todo fix broken encapsulation
    server_handler.rpc_client = client
