# -*- coding: utf-8 -*-

from thriftpy.thrift import TMultiplexedProcessor, TMessageType


class TMultiplexedProtocol(object):
    """Multiplex the protocol by prepend service name to api for every api call.
    Can be used together with all original protocols.
    """

    def __init__(self, proto, service_name):
        self.service_name = service_name
        self._proto = proto

    def __getattr__(self, name):
        return getattr(self._proto, name)

    def write_message_begin(self, name, ttype, seqid):
        if ttype in (TMessageType.CALL, TMessageType.ONEWAY):
            self._proto.write_message_begin(
                self.service_name + TMultiplexedProcessor.SEPARATOR + name,
                ttype, seqid)
        else:
            self._proto.write_message_begin(name, ttype, seqid)


class TMultiplexedProtocolFactory(object):
    def __init__(self, proto_factory, service_name):
        self._proto_factory = proto_factory
        self.service_name = service_name

    def get_protocol(self, trans):
        proto = self._proto_factory.get_protocol(trans)
        return TMultiplexedProtocol(proto, self.service_name)
