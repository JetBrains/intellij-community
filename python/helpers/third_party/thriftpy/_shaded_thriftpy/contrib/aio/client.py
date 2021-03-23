# -*- coding: utf-8 -*-
import asyncio
import functools
from _shaded_thriftpy.thrift import args_to_kwargs
from _shaded_thriftpy.thrift import TApplicationException, TMessageType


class TAsyncClient:

    def __init__(self, service, iprot, oprot=None):
        self._service = service
        self._iprot = self._oprot = iprot
        if oprot is not None:
            self._oprot = oprot
        self._seqid = 0

    def __getattr__(self, _api):
        if _api in self._service.thrift_services:
            return functools.partial(self._req, _api)

        raise AttributeError("{} instance has no attribute '{}'".format(
            self.__class__.__name__, _api))

    def __dir__(self):
        return self._service.thrift_services

    @asyncio.coroutine
    def _req(self, _api, *args, **kwargs):
        try:
            kwargs = args_to_kwargs(getattr(self._service, _api + "_args").thrift_spec,
                          *args, **kwargs)
        except ValueError as e:
            raise TApplicationException(
                    TApplicationException.UNKNOWN_METHOD,
                    'missing required argument {arg} for {service}.{api}'.format(
                        arg=e.args[0], service=self._service.__name__, api=_api))
        result_cls = getattr(self._service, _api + "_result")

        yield from self._send(_api, **kwargs)
        # wait result only if non-oneway
        if not getattr(result_cls, "oneway"):
            return (yield from self._recv(_api))

    @asyncio.coroutine
    def _send(self, _api, **kwargs):
        self._oprot.write_message_begin(_api, TMessageType.CALL, self._seqid)
        args = getattr(self._service, _api + "_args")()
        for k, v in kwargs.items():
            setattr(args, k, v)
        self._oprot.write_struct(args)
        self._oprot.write_message_end()
        yield from self._oprot.trans.flush()

    @asyncio.coroutine
    def _recv(self, _api):
        fname, mtype, rseqid = yield from self._iprot.read_message_begin()
        if mtype == TMessageType.EXCEPTION:
            x = TApplicationException()
            yield from self._iprot.read_struct(x)
            yield from self._iprot.read_message_end()
            raise x
        result = getattr(self._service, _api + "_result")()
        yield from self._iprot.read_struct(result)
        yield from self._iprot.read_message_end()

        if hasattr(result, "success") and result.success is not None:
            return result.success

        # void api without throws
        if len(result.thrift_spec) == 0:
            return

        # check throws
        for k, v in result.__dict__.items():
            if k != "success" and v:
                raise v

        # no throws & not void api
        if hasattr(result, "success"):
            raise TApplicationException(TApplicationException.MISSING_RESULT)

    def close(self):
        self._iprot.trans.close()
        if self._iprot != self._oprot:
            self._oprot.trans.close()
