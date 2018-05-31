import threading

from pydev_console.thrift_transport import TBidirectionalClientTransport, TSyncClient
from thriftpy.protocol import TBinaryProtocolFactory
from thriftpy.server import TThreadedServer
from thriftpy.thrift import TProcessor


def make_rpc_client(client_service, host, port, proto_factory=TBinaryProtocolFactory()):
    # instantiate client
    transport = TBidirectionalClientTransport(host, port)
    protocol = proto_factory.get_protocol(transport)
    transport.open()

    client = TSyncClient(client_service, protocol)

    server_transport = transport.get_server_transport()

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
