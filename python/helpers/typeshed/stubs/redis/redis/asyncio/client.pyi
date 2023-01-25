from _typeshed import Incomplete, Self
from collections.abc import AsyncIterator, Awaitable, Callable, Iterable, Mapping, MutableMapping, Sequence
from datetime import datetime, timedelta
from typing import Any, ClassVar, Generic, NoReturn, Protocol, overload
from typing_extensions import Literal, TypeAlias, TypedDict

from redis import RedisError
from redis.asyncio.connection import ConnectCallbackT, Connection, ConnectionPool
from redis.asyncio.lock import Lock
from redis.asyncio.retry import Retry
from redis.client import AbstractRedis, _CommandOptions, _Key, _StrType, _Value
from redis.commands import AsyncCoreCommands, AsyncSentinelCommands, RedisModuleCommands
from redis.typing import ChannelT, EncodableT, KeyT, PatternT

PubSubHandler: TypeAlias = Callable[[dict[str, str]], Awaitable[None]]

class ResponseCallbackProtocol(Protocol):
    def __call__(self, response: Any, **kwargs): ...

class AsyncResponseCallbackProtocol(Protocol):
    async def __call__(self, response: Any, **kwargs): ...

ResponseCallbackT: TypeAlias = ResponseCallbackProtocol | AsyncResponseCallbackProtocol

class Redis(AbstractRedis, RedisModuleCommands, AsyncCoreCommands[_StrType], AsyncSentinelCommands, Generic[_StrType]):
    response_callbacks: MutableMapping[str | bytes, ResponseCallbackT]
    auto_close_connection_pool: bool
    connection_pool: Any
    single_connection_client: Any
    connection: Any
    @classmethod
    def from_url(cls, url: str, **kwargs) -> Redis[Any]: ...
    def __init__(
        self,
        *,
        host: str = ...,
        port: int = ...,
        db: str | int = ...,
        password: str | None = ...,
        socket_timeout: float | None = ...,
        socket_connect_timeout: float | None = ...,
        socket_keepalive: bool | None = ...,
        socket_keepalive_options: Mapping[int, int | bytes] | None = ...,
        connection_pool: ConnectionPool | None = ...,
        unix_socket_path: str | None = ...,
        encoding: str = ...,
        encoding_errors: str = ...,
        decode_responses: bool = ...,
        retry_on_timeout: bool = ...,
        retry_on_error: list[type[RedisError]] | None = ...,
        ssl: bool = ...,
        ssl_keyfile: str | None = ...,
        ssl_certfile: str | None = ...,
        ssl_cert_reqs: str = ...,
        ssl_ca_certs: str | None = ...,
        ssl_ca_data: str | None = ...,
        ssl_check_hostname: bool = ...,
        max_connections: int | None = ...,
        single_connection_client: bool = ...,
        health_check_interval: int = ...,
        client_name: str | None = ...,
        username: str | None = ...,
        retry: Retry | None = ...,
        auto_close_connection_pool: bool = ...,
        redis_connect_func: ConnectCallbackT | None = ...,
    ) -> None: ...
    def __await__(self): ...
    async def initialize(self: Self) -> Self: ...
    def set_response_callback(self, command: str, callback: ResponseCallbackT): ...
    def load_external_module(self, funcname, func) -> None: ...
    def pipeline(self, transaction: bool = ..., shard_hint: str | None = ...) -> Pipeline[_StrType]: ...
    async def transaction(
        self,
        func: Callable[[Pipeline[_StrType]], Any | Awaitable[Any]],
        *watches: KeyT,
        shard_hint: str | None = ...,
        value_from_callable: bool = ...,
        watch_delay: float | None = ...,
    ): ...
    def lock(
        self,
        name: KeyT,
        timeout: float | None = ...,
        sleep: float = ...,
        blocking_timeout: float | None = ...,
        lock_class: type[Lock] | None = ...,
        thread_local: bool = ...,
    ) -> Lock: ...
    def pubsub(self, **kwargs) -> PubSub: ...
    def monitor(self) -> Monitor: ...
    def client(self) -> Redis[_StrType]: ...
    async def __aenter__(self: Self) -> Self: ...
    async def __aexit__(self, exc_type, exc_value, traceback) -> None: ...
    def __del__(self, _warnings: Any = ...) -> None: ...
    async def close(self, close_connection_pool: bool | None = ...) -> None: ...
    async def execute_command(self, *args, **options): ...
    async def parse_response(self, connection: Connection, command_name: str | bytes, **options): ...

StrictRedis = Redis

class MonitorCommandInfo(TypedDict):
    time: float
    db: int
    client_address: str
    client_port: str
    client_type: str
    command: str

class Monitor:
    monitor_re: Any
    command_re: Any
    connection_pool: Any
    connection: Any
    def __init__(self, connection_pool: ConnectionPool) -> None: ...
    async def connect(self) -> None: ...
    async def __aenter__(self): ...
    async def __aexit__(self, *args) -> None: ...
    async def next_command(self) -> MonitorCommandInfo: ...
    async def listen(self) -> AsyncIterator[MonitorCommandInfo]: ...

class PubSub:
    PUBLISH_MESSAGE_TYPES: ClassVar[tuple[str, ...]]
    UNSUBSCRIBE_MESSAGE_TYPES: ClassVar[tuple[str, ...]]
    HEALTH_CHECK_MESSAGE: ClassVar[str]
    connection_pool: Any
    shard_hint: str | None
    ignore_subscribe_messages: bool
    connection: Any
    encoder: Any
    health_check_response: Iterable[str | bytes]
    channels: Any
    pending_unsubscribe_channels: Any
    patterns: Any
    pending_unsubscribe_patterns: Any
    def __init__(
        self,
        connection_pool: ConnectionPool,
        shard_hint: str | None = ...,
        ignore_subscribe_messages: bool = ...,
        encoder: Any | None = ...,
    ) -> None: ...
    async def __aenter__(self): ...
    async def __aexit__(self, exc_type, exc_value, traceback) -> None: ...
    def __del__(self) -> None: ...
    async def reset(self) -> None: ...
    def close(self) -> Awaitable[NoReturn]: ...
    async def on_connect(self, connection: Connection): ...
    @property
    def subscribed(self) -> bool: ...
    async def execute_command(self, *args: EncodableT): ...
    async def parse_response(self, block: bool = ..., timeout: float = ...): ...
    async def check_health(self) -> None: ...
    async def psubscribe(self, *args: ChannelT, **kwargs: PubSubHandler): ...
    def punsubscribe(self, *args: ChannelT) -> Awaitable[Any]: ...
    async def subscribe(self, *args: ChannelT, **kwargs: Callable[..., Any]): ...
    def unsubscribe(self, *args) -> Awaitable[Any]: ...
    async def listen(self) -> AsyncIterator[Any]: ...
    async def get_message(self, ignore_subscribe_messages: bool = ..., timeout: float = ...): ...
    def ping(self, message: Any | None = ...) -> Awaitable[Any]: ...
    async def handle_message(self, response, ignore_subscribe_messages: bool = ...): ...
    async def run(self, *, exception_handler: PSWorkerThreadExcHandlerT | None = ..., poll_timeout: float = ...) -> None: ...

class PubsubWorkerExceptionHandler(Protocol):
    def __call__(self, e: BaseException, pubsub: PubSub): ...

class AsyncPubsubWorkerExceptionHandler(Protocol):
    async def __call__(self, e: BaseException, pubsub: PubSub): ...

PSWorkerThreadExcHandlerT: TypeAlias = PubsubWorkerExceptionHandler | AsyncPubsubWorkerExceptionHandler
CommandT: TypeAlias = tuple[tuple[str | bytes, ...], Mapping[str, Any]]
CommandStackT: TypeAlias = list[CommandT]

class Pipeline(Redis[_StrType], Generic[_StrType]):
    UNWATCH_COMMANDS: ClassVar[set[str]]
    connection_pool: Any
    connection: Any
    response_callbacks: Any
    is_transaction: bool
    shard_hint: str | None
    watching: bool
    command_stack: Any
    scripts: Any
    explicit_transaction: bool
    def __init__(
        self,
        connection_pool: ConnectionPool,
        response_callbacks: MutableMapping[str | bytes, ResponseCallbackT],
        transaction: bool,
        shard_hint: str | None,
    ) -> None: ...
    async def __aenter__(self: Self) -> Self: ...
    async def __aexit__(self, exc_type, exc_value, traceback) -> None: ...
    def __await__(self): ...
    def __len__(self) -> int: ...
    def __bool__(self) -> bool: ...
    async def reset(self) -> None: ...  # type: ignore[override]
    def multi(self) -> None: ...
    def execute_command(self, *args, **kwargs) -> Pipeline[_StrType] | Awaitable[Pipeline[_StrType]]: ...
    async def immediate_execute_command(self, *args, **options): ...
    def pipeline_execute_command(self, *args, **options): ...
    def raise_first_error(self, commands: CommandStackT, response: Iterable[Any]): ...
    def annotate_exception(self, exception: Exception, number: int, command: Iterable[object]) -> None: ...
    async def parse_response(self, connection: Connection, command_name: str | bytes, **options): ...
    async def load_scripts(self) -> None: ...
    async def execute(self, raise_on_error: bool = ...): ...
    async def discard(self) -> None: ...
    async def watch(self, *names: KeyT) -> bool: ...
    async def unwatch(self) -> bool: ...
    # region acl commands
    def acl_cat(self, category: str | None = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_deluser(self, *username: str, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_genpass(self, bits: int | None = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_getuser(self, username: str, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_help(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_list(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_log(self, count: int | None = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_log_reset(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_load(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_save(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_setuser(  # type: ignore[override]
        self,
        username: str,
        enabled: bool = ...,
        nopass: bool = ...,
        passwords: Sequence[str] | None = ...,
        hashed_passwords: Sequence[str] | None = ...,
        categories: Sequence[str] | None = ...,
        commands: Sequence[str] | None = ...,
        keys: Sequence[str] | None = ...,
        channels: Iterable[ChannelT] | None = ...,
        selectors: Iterable[tuple[str, KeyT]] | None = ...,
        reset: bool = ...,
        reset_keys: bool = ...,
        reset_passwords: bool = ...,
        **kwargs: _CommandOptions,
    ) -> Pipeline[_StrType]: ...
    def acl_users(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def acl_whoami(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    # endregion
    # region cluster commands
    def cluster(self, cluster_arg: str, *args, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def readwrite(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def readonly(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    # endregion
    # region BasicKey commands
    def append(self, key, value) -> Any: ...  # type: ignore[override]
    def bitcount(self, key: _Key, start: int | None = ..., end: int | None = ..., mode: str | None = ...) -> Any: ...  # type: ignore[override]
    def bitfield(self, key, default_overflow: Incomplete | None = ...) -> Any: ...  # type: ignore[override]
    def bitop(self, operation, dest, *keys) -> Any: ...  # type: ignore[override]
    def bitpos(self, key: _Key, bit: int, start: int | None = ..., end: int | None = ..., mode: str | None = ...) -> Any: ...  # type: ignore[override]
    def copy(self, source, destination, destination_db: Incomplete | None = ..., replace: bool = ...) -> Any: ...  # type: ignore[override]
    def decr(self, name, amount: int = ...) -> Any: ...  # type: ignore[override]
    def decrby(self, name, amount: int = ...) -> Any: ...  # type: ignore[override]
    def delete(self, *names: _Key) -> Any: ...  # type: ignore[override]
    def dump(self, name: _Key) -> Any: ...  # type: ignore[override]
    def exists(self, *names: _Key) -> Any: ...  # type: ignore[override]
    def expire(  # type: ignore[override]
        self, name: _Key, time: int | timedelta, nx: bool = ..., xx: bool = ..., gt: bool = ..., lt: bool = ...
    ) -> Any: ...
    def expireat(self, name, when, nx: bool = ..., xx: bool = ..., gt: bool = ..., lt: bool = ...) -> Any: ...  # type: ignore[override]
    def get(self, name: _Key) -> Any: ...  # type: ignore[override]
    def getdel(self, name: _Key) -> Any: ...  # type: ignore[override]
    def getex(  # type: ignore[override]
        self,
        name,
        ex: Incomplete | None = ...,
        px: Incomplete | None = ...,
        exat: Incomplete | None = ...,
        pxat: Incomplete | None = ...,
        persist: bool = ...,
    ) -> Any: ...
    def getbit(self, name: _Key, offset: int) -> Any: ...  # type: ignore[override]
    def getrange(self, key, start, end) -> Any: ...  # type: ignore[override]
    def getset(self, name, value) -> Any: ...  # type: ignore[override]
    def incr(self, name: _Key, amount: int = ...) -> Any: ...  # type: ignore[override]
    def incrby(self, name: _Key, amount: int = ...) -> Any: ...  # type: ignore[override]
    def incrbyfloat(self, name: _Key, amount: float = ...) -> Any: ...  # type: ignore[override]
    def keys(self, pattern: _Key = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def lmove(  # type: ignore[override]
        self, first_list: _Key, second_list: _Key, src: Literal["LEFT", "RIGHT"] = ..., dest: Literal["LEFT", "RIGHT"] = ...
    ) -> Any: ...
    def blmove(  # type: ignore[override]
        self,
        first_list: _Key,
        second_list: _Key,
        timeout: float,
        src: Literal["LEFT", "RIGHT"] = ...,
        dest: Literal["LEFT", "RIGHT"] = ...,
    ) -> Any: ...
    def mget(self, keys: _Key | Iterable[_Key], *args: _Key) -> Any: ...  # type: ignore[override]
    def mset(self, mapping: Mapping[_Key, _Value]) -> Any: ...  # type: ignore[override]
    def msetnx(self, mapping: Mapping[_Key, _Value]) -> Any: ...  # type: ignore[override]
    def move(self, name: _Key, db: int) -> Any: ...  # type: ignore[override]
    def persist(self, name: _Key) -> Any: ...  # type: ignore[override]
    def pexpire(  # type: ignore[override]
        self, name: _Key, time: int | timedelta, nx: bool = ..., xx: bool = ..., gt: bool = ..., lt: bool = ...
    ) -> Any: ...
    def pexpireat(  # type: ignore[override]
        self, name: _Key, when: int | datetime, nx: bool = ..., xx: bool = ..., gt: bool = ..., lt: bool = ...
    ) -> Any: ...
    def psetex(self, name, time_ms, value) -> Any: ...  # type: ignore[override]
    def pttl(self, name: _Key) -> Any: ...  # type: ignore[override]
    def hrandfield(self, key, count: Incomplete | None = ..., withvalues: bool = ...) -> Any: ...  # type: ignore[override]
    def randomkey(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def rename(self, src, dst) -> Any: ...  # type: ignore[override]
    def renamenx(self, src, dst) -> Any: ...  # type: ignore[override]
    def restore(  # type: ignore[override]
        self,
        name,
        ttl,
        value,
        replace: bool = ...,
        absttl: bool = ...,
        idletime: Incomplete | None = ...,
        frequency: Incomplete | None = ...,
    ) -> Any: ...
    def set(  # type: ignore[override]
        self,
        name: _Key,
        value: _Value,
        ex: None | int | timedelta = ...,
        px: None | int | timedelta = ...,
        nx: bool = ...,
        xx: bool = ...,
        keepttl: bool = ...,
        get: bool = ...,
        exat: Incomplete | None = ...,
        pxat: Incomplete | None = ...,
    ) -> Any: ...
    def setbit(self, name: _Key, offset: int, value: int) -> Any: ...  # type: ignore[override]
    def setex(self, name: _Key, time: int | timedelta, value: _Value) -> Any: ...  # type: ignore[override]
    def setnx(self, name: _Key, value: _Value) -> Any: ...  # type: ignore[override]
    def setrange(self, name, offset, value) -> Any: ...  # type: ignore[override]
    def stralgo(  # type: ignore[override]
        self,
        algo,
        value1,
        value2,
        specific_argument: str = ...,
        len: bool = ...,
        idx: bool = ...,
        minmatchlen: Incomplete | None = ...,
        withmatchlen: bool = ...,
        **kwargs: _CommandOptions,
    ) -> Any: ...
    def strlen(self, name) -> Any: ...  # type: ignore[override]
    def substr(self, name, start, end: int = ...) -> Any: ...  # type: ignore[override]
    def touch(self, *args) -> Any: ...  # type: ignore[override]
    def ttl(self, name: _Key) -> Any: ...  # type: ignore[override]
    def type(self, name) -> Any: ...  # type: ignore[override]
    def unlink(self, *names: _Key) -> Any: ...  # type: ignore[override]
    # endregion
    # region hyperlog commands
    def pfadd(self, name: _Key, *values: _Value) -> Any: ...  # type: ignore[override]
    def pfcount(self, name: _Key) -> Any: ...  # type: ignore[override]
    def pfmerge(self, dest: _Key, *sources: _Key) -> Any: ...  # type: ignore[override]
    # endregion
    # region hash commands
    def hdel(self, name: _Key, *keys: _Key) -> Any: ...  # type: ignore[override]
    def hexists(self, name: _Key, key: _Key) -> Any: ...  # type: ignore[override]
    def hget(self, name: _Key, key: _Key) -> Any: ...  # type: ignore[override]
    def hgetall(self, name: _Key) -> Any: ...  # type: ignore[override]
    def hincrby(self, name: _Key, key: _Key, amount: int = ...) -> Any: ...  # type: ignore[override]
    def hincrbyfloat(self, name: _Key, key: _Key, amount: float = ...) -> Any: ...  # type: ignore[override]
    def hkeys(self, name: _Key) -> Any: ...  # type: ignore[override]
    def hlen(self, name: _Key) -> Any: ...  # type: ignore[override]
    @overload
    def hset(  # type: ignore[override]
        self, name: _Key, key: _Key, value: _Value, mapping: Mapping[_Key, _Value] | None = ..., items: Incomplete | None = ...
    ) -> Any: ...
    @overload
    def hset(self, name: _Key, key: None, value: None, mapping: Mapping[_Key, _Value], items: Incomplete | None = ...) -> Any: ...  # type: ignore[override]
    @overload
    def hset(self, name: _Key, *, mapping: Mapping[_Key, _Value], items: Incomplete | None = ...) -> Any: ...  # type: ignore[override]
    def hsetnx(self, name: _Key, key: _Key, value: _Value) -> Any: ...  # type: ignore[override]
    def hmset(self, name: _Key, mapping: Mapping[_Key, _Value]) -> Any: ...  # type: ignore[override]
    def hmget(self, name: _Key, keys: _Key | Iterable[_Key], *args: _Key) -> Any: ...  # type: ignore[override]
    def hvals(self, name: _Key) -> Any: ...  # type: ignore[override]
    def hstrlen(self, name, key) -> Any: ...  # type: ignore[override]
    # endregion
    # region geo commands
    def geoadd(self, name, values, nx: bool = ..., xx: bool = ..., ch: bool = ...) -> Any: ...  # type: ignore[override]
    def geodist(self, name, place1, place2, unit: Incomplete | None = ...) -> Any: ...  # type: ignore[override]
    def geohash(self, name, *values) -> Any: ...  # type: ignore[override]
    def geopos(self, name, *values) -> Any: ...  # type: ignore[override]
    def georadius(  # type: ignore[override]
        self,
        name,
        longitude,
        latitude,
        radius,
        unit: Incomplete | None = ...,
        withdist: bool = ...,
        withcoord: bool = ...,
        withhash: bool = ...,
        count: Incomplete | None = ...,
        sort: Incomplete | None = ...,
        store: Incomplete | None = ...,
        store_dist: Incomplete | None = ...,
        any: bool = ...,
    ) -> Any: ...
    def georadiusbymember(  # type: ignore[override]
        self,
        name,
        member,
        radius,
        unit: Incomplete | None = ...,
        withdist: bool = ...,
        withcoord: bool = ...,
        withhash: bool = ...,
        count: Incomplete | None = ...,
        sort: Incomplete | None = ...,
        store: Incomplete | None = ...,
        store_dist: Incomplete | None = ...,
        any: bool = ...,
    ) -> Any: ...
    def geosearch(  # type: ignore[override]
        self,
        name,
        member: Incomplete | None = ...,
        longitude: Incomplete | None = ...,
        latitude: Incomplete | None = ...,
        unit: str = ...,
        radius: Incomplete | None = ...,
        width: Incomplete | None = ...,
        height: Incomplete | None = ...,
        sort: Incomplete | None = ...,
        count: Incomplete | None = ...,
        any: bool = ...,
        withcoord: bool = ...,
        withdist: bool = ...,
        withhash: bool = ...,
    ) -> Any: ...
    def geosearchstore(  # type: ignore[override]
        self,
        dest,
        name,
        member: Incomplete | None = ...,
        longitude: Incomplete | None = ...,
        latitude: Incomplete | None = ...,
        unit: str = ...,
        radius: Incomplete | None = ...,
        width: Incomplete | None = ...,
        height: Incomplete | None = ...,
        sort: Incomplete | None = ...,
        count: Incomplete | None = ...,
        any: bool = ...,
        storedist: bool = ...,
    ) -> Any: ...
    # endregion
    # region list commands
    @overload
    def blpop(self, keys: _Value | Iterable[_Value], timeout: Literal[0] | None = ...) -> Any: ...  # type: ignore[override]
    @overload
    def blpop(self, keys: _Value | Iterable[_Value], timeout: float) -> Any: ...  # type: ignore[override]
    @overload
    def brpop(self, keys: _Value | Iterable[_Value], timeout: Literal[0] | None = ...) -> Any: ...  # type: ignore[override]
    @overload
    def brpop(self, keys: _Value | Iterable[_Value], timeout: float) -> Any: ...  # type: ignore[override]
    def brpoplpush(self, src, dst, timeout: int | None = ...) -> Any: ...  # type: ignore[override]
    def lindex(self, name: _Key, index: int) -> Any: ...  # type: ignore[override]
    def linsert(  # type: ignore[override]
        self, name: _Key, where: Literal["BEFORE", "AFTER", "before", "after"], refvalue: _Value, value: _Value
    ) -> Any: ...
    def llen(self, name: _Key) -> Any: ...  # type: ignore[override]
    def lpop(self, name, count: int | None = ...) -> Any: ...  # type: ignore[override]
    def lpush(self, name: _Value, *values: _Value) -> Any: ...  # type: ignore[override]
    def lpushx(self, name, value) -> Any: ...  # type: ignore[override]
    def lrange(self, name: _Key, start: int, end: int) -> Any: ...  # type: ignore[override]
    def lrem(self, name: _Key, count: int, value: _Value) -> Any: ...  # type: ignore[override]
    def lset(self, name: _Key, index: int, value: _Value) -> Any: ...  # type: ignore[override]
    def ltrim(self, name: _Key, start: int, end: int) -> Any: ...  # type: ignore[override]
    def rpop(self, name, count: int | None = ...) -> Any: ...  # type: ignore[override]
    def rpoplpush(self, src, dst) -> Any: ...  # type: ignore[override]
    def rpush(self, name: _Value, *values: _Value) -> Any: ...  # type: ignore[override]
    def rpushx(self, name, value) -> Any: ...  # type: ignore[override]
    def lpos(self, name, value, rank: Incomplete | None = ..., count: Incomplete | None = ..., maxlen: Incomplete | None = ...) -> Any: ...  # type: ignore[override]
    @overload  # type: ignore[override]
    def sort(
        self,
        name: _Key,
        start: int | None = ...,
        num: int | None = ...,
        by: _Key | None = ...,
        get: _Key | Sequence[_Key] | None = ...,
        desc: bool = ...,
        alpha: bool = ...,
        store: None = ...,
        groups: bool = ...,
    ) -> list[_StrType]: ...
    @overload  # type: ignore[override]
    def sort(
        self,
        name: _Key,
        start: int | None = ...,
        num: int | None = ...,
        by: _Key | None = ...,
        get: _Key | Sequence[_Key] | None = ...,
        desc: bool = ...,
        alpha: bool = ...,
        *,
        store: _Key,
        groups: bool = ...,
    ) -> Any: ...
    @overload  # type: ignore[override]
    def sort(
        self,
        name: _Key,
        start: int | None,
        num: int | None,
        by: _Key | None,
        get: _Key | Sequence[_Key] | None,
        desc: bool,
        alpha: bool,
        store: _Key,
        groups: bool = ...,
    ) -> Any: ...
    # endregion
    # region scan commands
    def scan(  # type: ignore[override]
        self,
        cursor: int = ...,
        match: _Key | None = ...,
        count: int | None = ...,
        _type: str | None = ...,
        **kwargs: _CommandOptions,
    ) -> Any: ...
    def sscan(self, name: _Key, cursor: int = ..., match: _Key | None = ..., count: int | None = ...) -> Any: ...  # type: ignore[override]
    def hscan(self, name: _Key, cursor: int = ..., match: _Key | None = ..., count: int | None = ...) -> Any: ...  # type: ignore[override]
    @overload  # type: ignore[override]
    def zscan(self, name: _Key, cursor: int = ..., match: _Key | None = ..., count: int | None = ...) -> Any: ...
    @overload  # type: ignore[override]
    def zscan(
        self,
        name: _Key,
        cursor: int = ...,
        match: _Key | None = ...,
        count: int | None = ...,
        *,
        score_cast_func: Callable[[_StrType], Any],
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zscan(
        self, name: _Key, cursor: int, match: _Key | None, count: int | None, score_cast_func: Callable[[_StrType], Any]
    ) -> Any: ...
    # endregion
    # region set commands
    def sadd(self, name: _Key, *values: _Value) -> Any: ...  # type: ignore[override]
    def scard(self, name: _Key) -> Any: ...  # type: ignore[override]
    def sdiff(self, keys: _Key | Iterable[_Key], *args: _Key) -> Any: ...  # type: ignore[override]
    def sdiffstore(self, dest: _Key, keys: _Key | Iterable[_Key], *args: _Key) -> Any: ...  # type: ignore[override]
    def sinter(self, keys: _Key | Iterable[_Key], *args: _Key) -> Any: ...  # type: ignore[override]
    def sinterstore(self, dest: _Key, keys: _Key | Iterable[_Key], *args: _Key) -> Any: ...  # type: ignore[override]
    def sismember(self, name: _Key, value: _Value) -> Any: ...  # type: ignore[override]
    def smembers(self, name: _Key) -> Any: ...  # type: ignore[override]
    def smismember(self, name, values, *args) -> Any: ...  # type: ignore[override]
    def smove(self, src: _Key, dst: _Key, value: _Value) -> Any: ...  # type: ignore[override]
    @overload  # type: ignore[override]
    def spop(self, name: _Key, count: None = ...) -> Any: ...
    @overload  # type: ignore[override]
    def spop(self, name: _Key, count: int) -> Any: ...
    @overload  # type: ignore[override]
    def srandmember(self, name: _Key, number: None = ...) -> Any: ...
    @overload  # type: ignore[override]
    def srandmember(self, name: _Key, number: int) -> Any: ...
    def srem(self, name: _Key, *values: _Value) -> Any: ...  # type: ignore[override]
    def sunion(self, keys: _Key | Iterable[_Key], *args: _Key) -> Any: ...  # type: ignore[override]
    def sunionstore(self, dest: _Key, keys: _Key | Iterable[_Key], *args: _Key) -> Any: ...  # type: ignore[override]
    # endregion
    # region stream commands
    def xack(self, name, groupname, *ids) -> Any: ...  # type: ignore[override]
    def xadd(  # type: ignore[override]
        self,
        name,
        fields,
        id: str = ...,
        maxlen=...,
        approximate: bool = ...,
        nomkstream: bool = ...,
        minid: Incomplete | None = ...,
        limit: Incomplete | None = ...,
    ) -> Any: ...
    def xautoclaim(  # type: ignore[override]
        self,
        name,
        groupname,
        consumername,
        min_idle_time,
        start_id: int = ...,
        count: Incomplete | None = ...,
        justid: bool = ...,
    ) -> Any: ...
    def xclaim(  # type: ignore[override]
        self, name, groupname, consumername, min_idle_time, message_ids, idle=..., time=..., retrycount=..., force=..., justid=...
    ) -> Any: ...
    def xdel(self, name, *ids) -> Any: ...  # type: ignore[override]
    def xgroup_create(self, name, groupname, id: str = ..., mkstream: bool = ..., entries_read: int | None = ...) -> Any: ...  # type: ignore[override]
    def xgroup_delconsumer(self, name, groupname, consumername) -> Any: ...  # type: ignore[override]
    def xgroup_destroy(self, name, groupname) -> Any: ...  # type: ignore[override]
    def xgroup_createconsumer(self, name, groupname, consumername) -> Any: ...  # type: ignore[override]
    def xgroup_setid(self, name, groupname, id, entries_read: int | None = ...) -> Any: ...  # type: ignore[override]
    def xinfo_consumers(self, name, groupname) -> Any: ...  # type: ignore[override]
    def xinfo_groups(self, name) -> Any: ...  # type: ignore[override]
    def xinfo_stream(self, name, full: bool = ...) -> Any: ...  # type: ignore[override]
    def xlen(self, name: _Key) -> Any: ...  # type: ignore[override]
    def xpending(self, name, groupname) -> Any: ...  # type: ignore[override]
    def xpending_range(  # type: ignore[override]
        self, name: _Key, groupname, min, max, count: int, consumername: Incomplete | None = ..., idle: int | None = ...
    ) -> Any: ...
    def xrange(self, name, min: str = ..., max: str = ..., count: Incomplete | None = ...) -> Any: ...  # type: ignore[override]
    def xread(self, streams, count: Incomplete | None = ..., block: Incomplete | None = ...) -> Any: ...  # type: ignore[override]
    def xreadgroup(  # type: ignore[override]
        self, groupname, consumername, streams, count: Incomplete | None = ..., block: Incomplete | None = ..., noack: bool = ...
    ) -> Any: ...
    def xrevrange(self, name, max: str = ..., min: str = ..., count: Incomplete | None = ...) -> Any: ...  # type: ignore[override]
    def xtrim(  # type: ignore[override]
        self, name, maxlen: int | None = ..., approximate: bool = ..., minid: Incomplete | None = ..., limit: int | None = ...
    ) -> Any: ...
    # endregion
    # region sorted set commands
    def zadd(  # type: ignore[override]
        self,
        name: _Key,
        mapping: Mapping[_Key, _Value],
        nx: bool = ...,
        xx: bool = ...,
        ch: bool = ...,
        incr: bool = ...,
        gt: Incomplete | None = ...,
        lt: Incomplete | None = ...,
    ) -> Any: ...
    def zcard(self, name: _Key) -> Any: ...  # type: ignore[override]
    def zcount(self, name: _Key, min: _Value, max: _Value) -> Any: ...  # type: ignore[override]
    def zdiff(self, keys, withscores: bool = ...) -> Any: ...  # type: ignore[override]
    def zdiffstore(self, dest, keys) -> Any: ...  # type: ignore[override]
    def zincrby(self, name: _Key, amount: float, value: _Value) -> Any: ...  # type: ignore[override]
    def zinter(self, keys, aggregate: Incomplete | None = ..., withscores: bool = ...) -> Any: ...  # type: ignore[override]
    def zinterstore(self, dest: _Key, keys: Iterable[_Key], aggregate: Literal["SUM", "MIN", "MAX"] | None = ...) -> Any: ...  # type: ignore[override]
    def zlexcount(self, name: _Key, min: _Value, max: _Value) -> Any: ...  # type: ignore[override]
    def zpopmax(self, name: _Key, count: int | None = ...) -> Any: ...  # type: ignore[override]
    def zpopmin(self, name: _Key, count: int | None = ...) -> Any: ...  # type: ignore[override]
    def zrandmember(self, key, count: Incomplete | None = ..., withscores: bool = ...) -> Any: ...  # type: ignore[override]
    @overload  # type: ignore[override]
    def bzpopmax(self, keys: _Key | Iterable[_Key], timeout: Literal[0] = ...) -> Any: ...
    @overload  # type: ignore[override]
    def bzpopmax(self, keys: _Key | Iterable[_Key], timeout: float) -> Any: ...
    @overload  # type: ignore[override]
    def bzpopmin(self, keys: _Key | Iterable[_Key], timeout: Literal[0] = ...) -> Any: ...
    @overload  # type: ignore[override]
    def bzpopmin(self, keys: _Key | Iterable[_Key], timeout: float) -> Any: ...
    @overload  # type: ignore[override]
    def zrange(
        self,
        name: _Key,
        start: int,
        end: int,
        desc: bool,
        withscores: Literal[True],
        score_cast_func: Callable[[_StrType], Any],
        byscore: bool = ...,
        bylex: bool = ...,
        offset: int | None = ...,
        num: int | None = ...,
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrange(
        self,
        name: _Key,
        start: int,
        end: int,
        desc: bool,
        withscores: Literal[True],
        score_cast_func: Callable[[_StrType], float] = ...,
        byscore: bool = ...,
        bylex: bool = ...,
        offset: int | None = ...,
        num: int | None = ...,
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrange(
        self,
        name: _Key,
        start: int,
        end: int,
        *,
        withscores: Literal[True],
        score_cast_func: Callable[[_StrType], None],
        byscore: bool = ...,
        bylex: bool = ...,
        offset: int | None = ...,
        num: int | None = ...,
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrange(
        self,
        name: _Key,
        start: int,
        end: int,
        *,
        withscores: Literal[True],
        score_cast_func: Callable[[_StrType], float] = ...,
        byscore: bool = ...,
        bylex: bool = ...,
        offset: int | None = ...,
        num: int | None = ...,
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrange(
        self,
        name: _Key,
        start: int,
        end: int,
        desc: bool = ...,
        withscores: bool = ...,
        score_cast_func: Callable[[_StrType], Any] = ...,
        byscore: bool = ...,
        bylex: bool = ...,
        offset: int | None = ...,
        num: int | None = ...,
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrevrange(
        self, name: _Key, start: int, end: int, withscores: Literal[True], score_cast_func: Callable[[_StrType], None]
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrevrange(self, name: _Key, start: int, end: int, withscores: Literal[True]) -> Any: ...
    @overload  # type: ignore[override]
    def zrevrange(
        self, name: _Key, start: int, end: int, withscores: bool = ..., score_cast_func: Callable[[Any], Any] = ...
    ) -> Any: ...
    def zrangestore(  # type: ignore[override]
        self,
        dest,
        name,
        start,
        end,
        byscore: bool = ...,
        bylex: bool = ...,
        desc: bool = ...,
        offset: Incomplete | None = ...,
        num: Incomplete | None = ...,
    ) -> Any: ...
    def zrangebylex(self, name: _Key, min: _Value, max: _Value, start: int | None = ..., num: int | None = ...) -> Any: ...  # type: ignore[override]
    def zrevrangebylex(self, name: _Key, max: _Value, min: _Value, start: int | None = ..., num: int | None = ...) -> Any: ...  # type: ignore[override]
    @overload  # type: ignore[override]
    def zrangebyscore(
        self,
        name: _Key,
        min: _Value,
        max: _Value,
        start: int | None = ...,
        num: int | None = ...,
        *,
        withscores: Literal[True],
        score_cast_func: Callable[[_StrType], None],
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrangebyscore(
        self, name: _Key, min: _Value, max: _Value, start: int | None = ..., num: int | None = ..., *, withscores: Literal[True]
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrangebyscore(
        self,
        name: _Key,
        min: _Value,
        max: _Value,
        start: int | None = ...,
        num: int | None = ...,
        withscores: bool = ...,
        score_cast_func: Callable[[_StrType], Any] = ...,
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrevrangebyscore(
        self,
        name: _Key,
        max: _Value,
        min: _Value,
        start: int | None = ...,
        num: int | None = ...,
        *,
        withscores: Literal[True],
        score_cast_func: Callable[[_StrType], Any],
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrevrangebyscore(
        self, name: _Key, max: _Value, min: _Value, start: int | None = ..., num: int | None = ..., *, withscores: Literal[True]
    ) -> Any: ...
    @overload  # type: ignore[override]
    def zrevrangebyscore(
        self,
        name: _Key,
        max: _Value,
        min: _Value,
        start: int | None = ...,
        num: int | None = ...,
        withscores: bool = ...,
        score_cast_func: Callable[[_StrType], Any] = ...,
    ) -> Any: ...
    def zrank(self, name: _Key, value: _Value) -> Any: ...  # type: ignore[override]
    def zrem(self, name: _Key, *values: _Value) -> Any: ...  # type: ignore[override]
    def zremrangebylex(self, name: _Key, min: _Value, max: _Value) -> Any: ...  # type: ignore[override]
    def zremrangebyrank(self, name: _Key, min: int, max: int) -> Any: ...  # type: ignore[override]
    def zremrangebyscore(self, name: _Key, min: _Value, max: _Value) -> Any: ...  # type: ignore[override]
    def zrevrank(self, name: _Key, value: _Value) -> Any: ...  # type: ignore[override]
    def zscore(self, name: _Key, value: _Value) -> Any: ...  # type: ignore[override]
    def zunion(self, keys, aggregate: Incomplete | None = ..., withscores: bool = ...) -> Any: ...  # type: ignore[override]
    def zunionstore(self, dest: _Key, keys: Iterable[_Key], aggregate: Literal["SUM", "MIN", "MAX"] | None = ...) -> Any: ...  # type: ignore[override]
    def zmscore(self, key, members) -> Any: ...  # type: ignore[override]
    # endregion
    # region management commands
    def bgrewriteaof(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def bgsave(self, schedule: bool = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def role(self) -> Any: ...  # type: ignore[override]
    def client_kill(self, address: str, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_kill_filter(  # type: ignore[override]
        self,
        _id: Incomplete | None = ...,
        _type: Incomplete | None = ...,
        addr: Incomplete | None = ...,
        skipme: Incomplete | None = ...,
        laddr: Incomplete | None = ...,
        user: Incomplete | None = ...,
        **kwargs: _CommandOptions,
    ) -> Any: ...
    def client_info(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_list(self, _type: str | None = ..., client_id: list[str] = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_getname(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_getredir(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_reply(self, reply, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_id(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_tracking_on(  # type: ignore[override]
        self,
        clientid: Incomplete | None = ...,
        prefix=...,
        bcast: bool = ...,
        optin: bool = ...,
        optout: bool = ...,
        noloop: bool = ...,
    ) -> Any: ...
    def client_tracking_off(  # type: ignore[override]
        self,
        clientid: Incomplete | None = ...,
        prefix=...,
        bcast: bool = ...,
        optin: bool = ...,
        optout: bool = ...,
        noloop: bool = ...,
    ) -> Any: ...
    def client_tracking(  # type: ignore[override]
        self,
        on: bool = ...,
        clientid: Incomplete | None = ...,
        prefix=...,
        bcast: bool = ...,
        optin: bool = ...,
        optout: bool = ...,
        noloop: bool = ...,
        **kwargs: _CommandOptions,
    ) -> Any: ...
    def client_trackinginfo(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_setname(self, name: str, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_unblock(self, client_id, error: bool = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_pause(self, timeout, all: bool = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def client_unpause(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def command(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def command_info(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def command_count(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def config_get(self, pattern: PatternT = ..., *args: PatternT, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def config_set(self, name: KeyT, value: EncodableT, *args: KeyT | EncodableT, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def config_resetstat(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def config_rewrite(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def dbsize(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def debug_object(self, key, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def debug_segfault(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def echo(self, value: _Value, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def flushall(self, asynchronous: bool = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def flushdb(self, asynchronous: bool = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def sync(self) -> Any: ...  # type: ignore[override]
    def psync(self, replicationid, offset) -> Any: ...  # type: ignore[override]
    def swapdb(self, first, second, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def select(self, index, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def info(self, section: _Key | None = ..., *args: _Key, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def lastsave(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def lolwut(self, *version_numbers: _Value, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def migrate(  # type: ignore[override]
        self,
        host,
        port,
        keys,
        destination_db,
        timeout,
        copy: bool = ...,
        replace: bool = ...,
        auth: Incomplete | None = ...,
        **kwargs: _CommandOptions,
    ) -> Any: ...
    def object(self, infotype, key, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def memory_doctor(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def memory_help(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def memory_stats(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def memory_malloc_stats(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def memory_usage(self, key, samples: Incomplete | None = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def memory_purge(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def ping(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def quit(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def replicaof(self, *args, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def save(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def shutdown(  # type: ignore[override]
        self,
        save: bool = ...,
        nosave: bool = ...,
        now: bool = ...,
        force: bool = ...,
        abort: bool = ...,
        **kwargs: _CommandOptions,
    ) -> Any: ...
    def slaveof(self, host: Incomplete | None = ..., port: Incomplete | None = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def slowlog_get(self, num: Incomplete | None = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def slowlog_len(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def slowlog_reset(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def time(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def wait(self, num_replicas, timeout, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    # endregion
    # region module commands
    def module_load(self, path, *args) -> Any: ...  # type: ignore[override]
    def module_unload(self, name) -> Any: ...  # type: ignore[override]
    def module_list(self) -> Any: ...  # type: ignore[override]
    def command_getkeys(self, *args) -> Any: ...  # type: ignore[override]
    # endregion
    # region pubsub commands
    def publish(self, channel: _Key, message: _Key, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def pubsub_channels(self, pattern: _Key = ..., **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def pubsub_numpat(self, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    def pubsub_numsub(self, *args: _Key, **kwargs: _CommandOptions) -> Any: ...  # type: ignore[override]
    # endregion
    # region script commands
    def eval(self, script, numkeys, *keys_and_args) -> Any: ...  # type: ignore[override]
    def evalsha(self, sha, numkeys, *keys_and_args) -> Any: ...  # type: ignore[override]
    def script_exists(self, *args) -> Any: ...  # type: ignore[override]
    def script_debug(self, *args) -> Any: ...  # type: ignore[override]
    def script_flush(self, sync_type: Incomplete | None = ...) -> Any: ...  # type: ignore[override]
    def script_kill(self) -> Any: ...  # type: ignore[override]
    def script_load(self, script) -> Any: ...  # type: ignore[override]
    def register_script(self, script: str | _StrType) -> Any: ...  # type: ignore[override]
    # endregion
