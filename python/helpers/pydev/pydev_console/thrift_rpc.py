from pydev_console.thrift_transport import TBidirectionalClientTransport
from thriftpy.protocol import TBinaryProtocolFactory
from thriftpy.server import TThreadedServer
from thriftpy.thrift import TProcessor, TClient


def make_rpc_client(client_service, host, port, proto_factory=TBinaryProtocolFactory()):
    """

    :param client_service:
    :param server_service:
    :param server_handler:
    :param host: connection host
    :param port: connection port
    :param proto_factory: protocol factory for client
    :return:
    """

    # instantiate client
    transport = TBidirectionalClientTransport(host, port)
    protocol = proto_factory.get_protocol(transport)
    transport.open()

    client = TClient(client_service, protocol)

    server_transport = transport.get_server_transport()

    return client, server_transport


def make_rpc_server(server_transport, server_service, server_handler, proto_factory=TBinaryProtocolFactory()):
    # setup server
    processor = TProcessor(server_service, server_handler)

    return TThreadedServer(processor, server_transport, iprot_factory=proto_factory)
