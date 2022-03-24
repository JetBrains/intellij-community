from typing import Any, Text

from boto.connection import AWSAuthConnection
from boto.exception import BotoClientError

from .bucket import Bucket

def check_lowercase_bucketname(n): ...
def assert_case_insensitive(f): ...

class _CallingFormat:
    def get_bucket_server(self, server, bucket): ...
    def build_url_base(self, connection, protocol, server, bucket, key: str = ...): ...
    def build_host(self, server, bucket): ...
    def build_auth_path(self, bucket, key: str = ...): ...
    def build_path_base(self, bucket, key: str = ...): ...

class SubdomainCallingFormat(_CallingFormat):
    def get_bucket_server(self, server, bucket): ...

class VHostCallingFormat(_CallingFormat):
    def get_bucket_server(self, server, bucket): ...

class OrdinaryCallingFormat(_CallingFormat):
    def get_bucket_server(self, server, bucket): ...
    def build_path_base(self, bucket, key: str = ...): ...

class ProtocolIndependentOrdinaryCallingFormat(OrdinaryCallingFormat):
    def build_url_base(self, connection, protocol, server, bucket, key: str = ...): ...

class Location:
    DEFAULT: str
    EU: str
    EUCentral1: str
    USWest: str
    USWest2: str
    SAEast: str
    APNortheast: str
    APSoutheast: str
    APSoutheast2: str
    CNNorth1: str

class NoHostProvided: ...
class HostRequiredError(BotoClientError): ...

class S3Connection(AWSAuthConnection):
    DefaultHost: Any
    DefaultCallingFormat: Any
    QueryString: str
    calling_format: Any
    bucket_class: type[Bucket]
    anon: Any
    def __init__(
        self,
        aws_access_key_id: Any | None = ...,
        aws_secret_access_key: Any | None = ...,
        is_secure: bool = ...,
        port: Any | None = ...,
        proxy: Any | None = ...,
        proxy_port: Any | None = ...,
        proxy_user: Any | None = ...,
        proxy_pass: Any | None = ...,
        host: Any = ...,
        debug: int = ...,
        https_connection_factory: Any | None = ...,
        calling_format: Any = ...,
        path: str = ...,
        provider: str = ...,
        bucket_class: type[Bucket] = ...,
        security_token: Any | None = ...,
        suppress_consec_slashes: bool = ...,
        anon: bool = ...,
        validate_certs: Any | None = ...,
        profile_name: Any | None = ...,
    ) -> None: ...
    def __iter__(self): ...
    def __contains__(self, bucket_name): ...
    def set_bucket_class(self, bucket_class: type[Bucket]) -> None: ...
    def build_post_policy(self, expiration_time, conditions): ...
    def build_post_form_args(
        self,
        bucket_name,
        key,
        expires_in: int = ...,
        acl: Any | None = ...,
        success_action_redirect: Any | None = ...,
        max_content_length: Any | None = ...,
        http_method: str = ...,
        fields: Any | None = ...,
        conditions: Any | None = ...,
        storage_class: str = ...,
        server_side_encryption: Any | None = ...,
    ): ...
    def generate_url_sigv4(
        self,
        expires_in,
        method,
        bucket: str = ...,
        key: str = ...,
        headers: dict[Text, Text] | None = ...,
        force_http: bool = ...,
        response_headers: dict[Text, Text] | None = ...,
        version_id: Any | None = ...,
        iso_date: Any | None = ...,
    ): ...
    def generate_url(
        self,
        expires_in,
        method,
        bucket: str = ...,
        key: str = ...,
        headers: dict[Text, Text] | None = ...,
        query_auth: bool = ...,
        force_http: bool = ...,
        response_headers: dict[Text, Text] | None = ...,
        expires_in_absolute: bool = ...,
        version_id: Any | None = ...,
    ): ...
    def get_all_buckets(self, headers: dict[Text, Text] | None = ...): ...
    def get_canonical_user_id(self, headers: dict[Text, Text] | None = ...): ...
    def get_bucket(self, bucket_name: Text, validate: bool = ..., headers: dict[Text, Text] | None = ...) -> Bucket: ...
    def head_bucket(self, bucket_name, headers: dict[Text, Text] | None = ...): ...
    def lookup(self, bucket_name, validate: bool = ..., headers: dict[Text, Text] | None = ...): ...
    def create_bucket(
        self, bucket_name, headers: dict[Text, Text] | None = ..., location: Any = ..., policy: Any | None = ...
    ): ...
    def delete_bucket(self, bucket, headers: dict[Text, Text] | None = ...): ...
    def make_request(self, method, bucket: str = ..., key: str = ..., headers: Any | None = ..., data: str = ..., query_args: Any | None = ..., sender: Any | None = ..., override_num_retries: Any | None = ..., retry_handler: Any | None = ..., *args, **kwargs): ...  # type: ignore # https://github.com/python/mypy/issues/1237
