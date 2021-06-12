# -*- coding: utf-8 -*-

"""
    _shaded_thriftpy.thrift
    ~~~~~~~~~~~~~~~~~~

    Thrift simplified.
"""

from __future__ import absolute_import

import functools
import linecache
import types

from ._compat import with_metaclass, PY3
if PY3:
    from itertools import zip_longest
else:
    from itertools import izip_longest as zip_longest


def args_to_kwargs(thrift_spec, *args, **kwargs):
    for item, value in zip_longest(sorted(thrift_spec.items()), args):
        arg_name = item[1][1]
        required = item[1][-1]
        if value is not None:
            kwargs[item[1][1]] = value
        if required and arg_name not in kwargs:
            raise ValueError(arg_name)
    return kwargs


def parse_spec(ttype, spec=None):
    name_map = TType._VALUES_TO_NAMES

    def _type(s):
        return parse_spec(*s) if isinstance(s, tuple) else name_map[s]

    if spec is None:
        return name_map[ttype]

    if ttype == TType.STRUCT:
        return spec.__name__

    if ttype in (TType.LIST, TType.SET):
        return "%s<%s>" % (name_map[ttype], _type(spec))

    if ttype == TType.MAP:
        return "MAP<%s, %s>" % (_type(spec[0]), _type(spec[1]))


def init_func_generator(cls, spec):
    """Generate `__init__` function based on TPayload.default_spec

    For example::

        spec = [('name', 'Alice'), ('number', None)]

    will generate a types.FunctionType object representing::

        def __init__(self, name='Alice', number=None):
            self.name = name
            self.number = number
    """
    if not spec:
        def __init__(self):
            pass
        return __init__

    varnames, defaults = zip(*spec)

    args = ', '.join(map('{0[0]}={0[1]!r}'.format, spec))
    init = "def __init__(self, {}):\n".format(args)
    init += "\n".join(map('    self.{0} = {0}'.format, varnames))

    name = '<generated {}.__init__>'.format(cls.__name__)
    code = compile(init, name, 'exec')
    func = next(c for c in code.co_consts if isinstance(c, types.CodeType))

    # Add a fake linecache entry so debuggers and the traceback module can
    # better understand our generated code.
    linecache.cache[name] = (len(init), None, init.splitlines(True), name)

    return types.FunctionType(func, {}, argdefs=defaults)


class TType(object):
    STOP = 0
    VOID = 1
    BOOL = 2
    BYTE = 3
    I08 = 3
    DOUBLE = 4
    I16 = 6
    I32 = 8
    I64 = 10
    STRING = 11
    UTF7 = 11
    STRUCT = 12
    MAP = 13
    SET = 14
    LIST = 15
    UTF8 = 16
    UTF16 = 17
    BINARY = 18

    _VALUES_TO_NAMES = {
        STOP: 'STOP',
        VOID: 'VOID',
        BOOL: 'BOOL',
        BYTE: 'BYTE',
        I08: 'BYTE',
        DOUBLE: 'DOUBLE',
        I16: 'I16',
        I32: 'I32',
        I64: 'I64',
        STRING: 'STRING',
        UTF7: 'STRING',
        STRUCT: 'STRUCT',
        MAP: 'MAP',
        SET: 'SET',
        LIST: 'LIST',
        UTF8: 'UTF8',
        UTF16: 'UTF16',
        BINARY: 'BINARY'
    }


class TMessageType(object):
    CALL = 1
    REPLY = 2
    EXCEPTION = 3
    ONEWAY = 4


class TPayloadMeta(type):

    def __new__(cls, name, bases, attrs):
        if "default_spec" in attrs:
            spec = attrs.pop("default_spec")
            attrs["__init__"] = init_func_generator(cls, spec)
        return super(TPayloadMeta, cls).__new__(cls, name, bases, attrs)


def gen_init(cls, thrift_spec=None, default_spec=None):
    if thrift_spec is not None:
        cls.thrift_spec = thrift_spec

    if default_spec is not None:
        cls.__init__ = init_func_generator(cls, default_spec)
    return cls


class TPayload(with_metaclass(TPayloadMeta, object)):

    __hash__ = None

    def read(self, iprot):
        iprot.read_struct(self)

    def write(self, oprot):
        oprot.write_struct(self)

    def __repr__(self):
        l = ['%s=%r' % (key, value) for key, value in self.__dict__.items()]
        return '%s(%s)' % (self.__class__.__name__, ', '.join(l))

    def __str__(self):
        return repr(self)

    def __eq__(self, other):
        return isinstance(other, self.__class__) and \
            self.__dict__ == other.__dict__

    def __ne__(self, other):
        return not self.__eq__(other)


class TClient(object):

    def __init__(self, service, iprot, oprot=None):
        self._service = service
        self._iprot = self._oprot = iprot
        if oprot is not None:
            self._oprot = oprot
        self._seqid = 0

    def __getattr__(self, _api):
        if _api in self._service.thrift_services:
            return functools.partial(self._req, _api)

        # close method is a reserved method name defined as below
        # so we need to handle it alone
        if _api == 'tclose':
            return functools.partial(self._req, 'close')

        raise AttributeError("{} instance has no attribute '{}'".format(
            self.__class__.__name__, _api))

    def __dir__(self):
        return self._service.thrift_services

    def _req(self, _api, *args, **kwargs):
        try:
            kwargs = args_to_kwargs(getattr(self._service, _api + "_args").thrift_spec,
                          *args, **kwargs)
        except ValueError as e:
            raise TApplicationException(
                    TApplicationException.UNKNOWN_METHOD,
                    '{arg} is required argument for {service}.{api}'.format(
                        arg=e.args[0], service=self._service.__name__, api=_api))

        result_cls = getattr(self._service, _api + "_result")

        self._send(_api, **kwargs)
        # wait result only if non-oneway
        if not getattr(result_cls, "oneway"):
            return self._recv(_api)

    def _send(self, _api, **kwargs):
        self._oprot.write_message_begin(_api, TMessageType.CALL, self._seqid)
        args = getattr(self._service, _api + "_args")()
        for k, v in kwargs.items():
            setattr(args, k, v)
        args.write(self._oprot)
        self._oprot.write_message_end()
        self._oprot.trans.flush()

    def _recv(self, _api):
        fname, mtype, rseqid = self._iprot.read_message_begin()
        if mtype == TMessageType.EXCEPTION:
            x = TApplicationException()
            x.read(self._iprot)
            self._iprot.read_message_end()
            raise x
        result = getattr(self._service, _api + "_result")()
        result.read(self._iprot)
        self._iprot.read_message_end()

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


class TProcessor(object):
    """Base class for processor, which works on two streams."""

    def __init__(self, service, handler):
        self._service = service
        self._handler = handler

    def process_in(self, iprot):
        api, type, seqid = iprot.read_message_begin()
        if api not in self._service.thrift_services:
            iprot.skip(TType.STRUCT)
            iprot.read_message_end()
            return api, seqid, TApplicationException(TApplicationException.UNKNOWN_METHOD), None  # noqa

        args = getattr(self._service, api + "_args")()
        args.read(iprot)
        iprot.read_message_end()
        result = getattr(self._service, api + "_result")()

        # convert kwargs to args
        api_args = [args.thrift_spec[k][1] for k in sorted(args.thrift_spec)]

        def call():
            f = getattr(self._handler, api)
            return f(*(args.__dict__[k] for k in api_args))

        return api, seqid, result, call

    def send_exception(self, oprot, api, exc, seqid):
        oprot.write_message_begin(api, TMessageType.EXCEPTION, seqid)
        exc.write(oprot)
        oprot.write_message_end()
        oprot.trans.flush()

    def send_result(self, oprot, api, result, seqid):
        oprot.write_message_begin(api, TMessageType.REPLY, seqid)
        result.write(oprot)
        oprot.write_message_end()
        oprot.trans.flush()

    def handle_exception(self, e, result):
        for k in sorted(result.thrift_spec):
            if result.thrift_spec[k][1] == "success":
                continue

            _, exc_name, exc_cls, _ = result.thrift_spec[k]
            if isinstance(e, exc_cls):
                setattr(result, exc_name, e)
                return True
        return False

    def process(self, iprot, oprot):
        api, seqid, result, call = self.process_in(iprot)

        if isinstance(result, TApplicationException):
            return self.send_exception(oprot, api, result, seqid)

        try:
            result.success = call()
        except TApplicationException as e:
            return self.send_exception(oprot, api, e, seqid)
        except Exception as e:
            # raise if api don't have throws
            if not self.handle_exception(e, result):
                raise

        if not result.oneway:
            self.send_result(oprot, api, result, seqid)


class TMultiplexedProcessor(TProcessor):
    SEPARATOR = ":"

    def __init__(self):
        self.processors = {}

    def register_processor(self, service_name, processor):
        if service_name in self.processors:
            raise TApplicationException(
                type=TApplicationException.INTERNAL_ERROR,
                message='processor for `{}` already registered'
                .format(service_name))
        self.processors[service_name] = processor

    def process_in(self, iprot):
        api, type, seqid = iprot.read_message_begin()
        if type not in (TMessageType.CALL, TMessageType.ONEWAY):
            raise TException("TMultiplexed protocol only supports CALL & ONEWAY")  # noqa
        if TMultiplexedProcessor.SEPARATOR not in api:
            raise TException("Service name not found in message. "
                             "You should use TMultiplexedProtocol in client.")

        service_name, api = api.split(TMultiplexedProcessor.SEPARATOR)
        if service_name not in self.processors:
            iprot.skip(TType.STRUCT)
            iprot.read_message_end()
            e = TApplicationException(TApplicationException.UNKNOWN_METHOD)
            return api, seqid, e, None

        proc = self.processors[service_name]
        args = getattr(proc._service, api + "_args")()
        args.read(iprot)
        iprot.read_message_end()
        result = getattr(proc._service, api + "_result")()

        # convert kwargs to args
        api_args = [args.thrift_spec[k][1] for k in sorted(args.thrift_spec)]

        def call():
            f = getattr(proc._handler, api)
            return f(*(args.__dict__[k] for k in api_args))

        return api, seqid, result, call


class TProcessorFactory(object):

    def __init__(self, processor_class, *args, **kwargs):
        self.args = args
        self.kwargs = kwargs

        self.processor_class = processor_class

    def get_processor(self):
        return self.processor_class(*self.args, **self.kwargs)


class TException(TPayload, Exception):
    """Base class for all thrift exceptions."""

    def __hash__(self):
        return id(self)

    def __eq__(self, other):
        return id(self) == id(other)


class TDecodeException(TException):
    def __init__(self, name, fid, field, value, ttype, spec=None):
        self.struct_name = name
        self.fid = fid
        self.field = field
        self.value = value

        self.type_repr = parse_spec(ttype, spec)

    def __str__(self):
        return (
            "Field '%s(%s)' of '%s' needs type '%s', "
            "but the value is `%r`"
        ) % (self.field, self.fid, self.struct_name, self.type_repr,
             self.value)


class TApplicationException(TException):
    """Application level thrift exceptions."""

    thrift_spec = {
        1: (TType.STRING, 'message', False),
        2: (TType.I32, 'type', False),
    }

    UNKNOWN = 0
    UNKNOWN_METHOD = 1
    INVALID_MESSAGE_TYPE = 2
    WRONG_METHOD_NAME = 3
    BAD_SEQUENCE_ID = 4
    MISSING_RESULT = 5
    INTERNAL_ERROR = 6
    PROTOCOL_ERROR = 7

    def __init__(self, type=UNKNOWN, message=None):
        super(TApplicationException, self).__init__()
        self.type = type
        self.message = message

    def __str__(self):
        if self.message:
            return self.message

        if self.type == self.UNKNOWN_METHOD:
            return 'Unknown method'
        elif self.type == self.INVALID_MESSAGE_TYPE:
            return 'Invalid message type'
        elif self.type == self.WRONG_METHOD_NAME:
            return 'Wrong method name'
        elif self.type == self.BAD_SEQUENCE_ID:
            return 'Bad sequence ID'
        elif self.type == self.MISSING_RESULT:
            return 'Missing result'
        else:
            return 'Default (unknown) TApplicationException'
