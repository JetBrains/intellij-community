import socket
import threading

from _pydev_comm.pydev_server import TSingleThreadedServer
from _pydev_comm.pydev_transport import TSyncClient, open_transports_as_client, _create_client_server_transports
from _shaded_thriftpy.protocol import TBinaryProtocolFactory
from _shaded_thriftpy.thrift import TProcessor


def make_rpc_client(client_service, host, port, proto_factory=TBinaryProtocolFactory(strict_read=False, strict_write=False)):
    client_transport, server_transport = open_transports_as_client((host, port))

    client_protocol = proto_factory.get_protocol(client_transport)

    client = TSyncClient(client_service, client_protocol)

    return client, server_transport


def start_rpc_server(server_transport, server_service, server_handler, proto_factory=TBinaryProtocolFactory(strict_read=False,
                                                                                                            strict_write=False)):
    # setup server
    processor = TProcessor(server_service, server_handler)
    server = TSingleThreadedServer(processor, server_transport, daemon=True, iprot_factory=proto_factory)
    server.serve()
    return server


def start_rpc_server_and_make_client(host, port, server_service, client_service, server_handler_factory,
                                     proto_factory=TBinaryProtocolFactory(strict_read=False, strict_write=False)):
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    server_socket.bind((host, port))
    server_socket.listen(1)

    t = threading.Thread(target=_rpc_server, args=(server_socket, server_service, client_service, server_handler_factory, proto_factory))
    t.daemon = True
    t.start()

    return server_socket


def _rpc_server(server_socket, server_service, client_service, server_handler_factory, proto_factory):
    client_socket, address = server_socket.accept()

    client_transport, server_transport = _create_client_server_transports(client_socket)

    client_protocol = proto_factory.get_protocol(client_transport)

    rpc_client = TSyncClient(client_service, client_protocol)

    server_handler = server_handler_factory(rpc_client)

    # setup server
    processor = TProcessor(server_service, server_handler)
    server = TSingleThreadedServer(processor, server_transport, daemon=True, iprot_factory=proto_factory)
    server.serve()
