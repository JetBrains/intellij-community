from __future__ import annotations

from typing import Optional, cast
from typing_extensions import assert_type

import grpc

# Channel options:
assert_type(grpc.insecure_channel("target", ()), grpc.Channel)
assert_type(grpc.insecure_channel("target", (("a", "b"),)), grpc.Channel)
assert_type(grpc.insecure_channel("target", (("a", "b"), ("c", "d"))), grpc.Channel)

# Local channel credentials:
creds = grpc.local_channel_credentials(grpc.LocalConnectionType.LOCAL_TCP)
assert_type(creds, grpc.ChannelCredentials)

# Other credential types:
assert_type(grpc.alts_channel_credentials(), grpc.ChannelCredentials)
assert_type(grpc.alts_server_credentials(), grpc.ServerCredentials)
assert_type(grpc.compute_engine_channel_credentials(grpc.CallCredentials("")), grpc.ChannelCredentials)
assert_type(grpc.insecure_server_credentials(), grpc.ServerCredentials)

# XDS credentials:
assert_type(
    grpc.xds_channel_credentials(grpc.local_channel_credentials(grpc.LocalConnectionType.LOCAL_TCP)), grpc.ChannelCredentials
)
assert_type(grpc.xds_server_credentials(grpc.insecure_server_credentials()), grpc.ServerCredentials)

# Channel ready future
channel = grpc.insecure_channel("target", ())
assert_type(grpc.channel_ready_future(channel).result(), None)

# Channel options supports list:
assert_type(grpc.insecure_channel("target", []), grpc.Channel)
assert_type(grpc.insecure_channel("target", [("a", "b")]), grpc.Channel)
assert_type(grpc.insecure_channel("target", [("a", "b"), ("c", "d")]), grpc.Channel)

# Client call details optionals:
call_details = grpc.ClientCallDetails()
assert_type(call_details.method, str)
assert_type(call_details.timeout, Optional[float])

# Call iterator
call_iter = cast(grpc._CallIterator[str], None)
for call in call_iter:
    assert_type(call, str)
assert_type(next(call_iter), str)
