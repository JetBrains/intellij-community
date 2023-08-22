# -*- coding: utf-8 -*-
import asyncio
from _shaded_thriftpy.thrift import TApplicationException, TType, TMessageType


class TAsyncProcessor(object):

    def __init__(self, service, handler):
        self._service = service
        self._handler = handler

    @asyncio.coroutine
    def process_in(self, iprot):
        api, type, seqid = yield from iprot.read_message_begin()
        if api not in self._service.thrift_services:
            yield from iprot.skip(TType.STRUCT)
            yield from iprot.read_message_end()
            return api, seqid, TApplicationException(TApplicationException.UNKNOWN_METHOD), None  # noqa

        args = getattr(self._service, api + "_args")()
        yield from iprot.read_struct(args)
        yield from iprot.read_message_end()
        result = getattr(self._service, api + "_result")()

        # convert kwargs to args
        api_args = [args.thrift_spec[k][1] for k in sorted(args.thrift_spec)]

        @asyncio.coroutine
        def call():
            f = getattr(self._handler, api)
            return (yield from f(*(args.__dict__[k] for k in api_args)))

        return api, seqid, result, call

    @asyncio.coroutine
    def send_exception(self, oprot, api, exc, seqid):
        oprot.write_message_begin(api, TMessageType.EXCEPTION, seqid)
        exc.write(oprot)
        oprot.write_message_end()
        yield from oprot.trans.flush()

    @asyncio.coroutine
    def send_result(self, oprot, api, result, seqid):
        oprot.write_message_begin(api, TMessageType.REPLY, seqid)
        oprot.write_struct(result)
        oprot.write_message_end()
        yield from oprot.trans.flush()

    def handle_exception(self, e, result):
        for k in sorted(result.thrift_spec):
            if result.thrift_spec[k][1] == "success":
                continue

            _, exc_name, exc_cls, _ = result.thrift_spec[k]
            if isinstance(e, exc_cls):
                setattr(result, exc_name, e)
                return True
        return False

    @asyncio.coroutine
    def process(self, iprot, oprot):
        api, seqid, result, call = yield from self.process_in(iprot)

        if isinstance(result, TApplicationException):
            return self.send_exception(oprot, api, result, seqid)

        try:
            result.success = yield from call()
        except Exception as e:
            # raise if api don't have throws
            if not self.handle_exception(e, result):
                raise

        if not result.oneway:
            yield from self.send_result(oprot, api, result, seqid)
