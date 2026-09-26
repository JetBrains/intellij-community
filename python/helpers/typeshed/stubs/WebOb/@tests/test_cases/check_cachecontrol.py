from __future__ import annotations

from typing import Any, Literal, Union
from typing_extensions import assert_type

from webob.cachecontrol import CacheControl
from webob.request import BaseRequest
from webob.response import Response

req = BaseRequest({})
res = Response()
assert_type(req.cache_control, CacheControl[Literal["request"]])
assert_type(res.cache_control, CacheControl[Literal["response"]])

assert_type(CacheControl.parse(""), CacheControl[None])
assert_type(CacheControl.parse("", type="request"), CacheControl[Literal["request"]])
assert_type(CacheControl.parse("", type="response"), CacheControl[Literal["response"]])

req_cc = req.cache_control
res_cc = res.cache_control
shared_cc = CacheControl.parse("")
assert_type(req_cc, CacheControl[Literal["request"]])
assert_type(res_cc, CacheControl[Literal["response"]])
assert_type(shared_cc, CacheControl[None])
any_cc = CacheControl[Any]({}, None)

assert_type(req_cc.max_stale, Union[int, Literal["*"], None])
res_cc.max_stale  # type: ignore
shared_cc.max_stale  # type: ignore
assert_type(any_cc.max_stale, Union[int, Literal["*"], None])

assert_type(req_cc.min_fresh, Union[int, None])
res_cc.min_fresh  # type: ignore
shared_cc.min_fresh  # type: ignore
assert_type(any_cc.min_fresh, Union[int, None])

assert_type(req_cc.only_if_cached, bool)
res_cc.only_if_cached  # type: ignore
shared_cc.only_if_cached  # type: ignore
assert_type(any_cc.only_if_cached, bool)

req_cc.public  # type: ignore
assert_type(res_cc.public, bool)
shared_cc.public  # type: ignore
assert_type(any_cc.public, bool)

# NOTE: pyright gets confused about the `Literal["*"]` the types match
req_cc.private  # type: ignore
assert_type(res_cc.private, Union[str, Literal["*"], None])  # pyright: ignore
shared_cc.private  # type: ignore
assert_type(any_cc.private, Union[str, Literal["*"], None])  # pyright: ignore

assert_type(req_cc.no_cache, Union[str, Literal["*"], None])  # pyright: ignore
assert_type(res_cc.no_cache, Union[str, Literal["*"], None])  # pyright: ignore
assert_type(shared_cc.no_cache, Union[str, Literal["*"], None])  # pyright: ignore
assert_type(any_cc.no_cache, Union[str, Literal["*"], None])  # pyright: ignore

assert_type(req_cc.no_store, bool)
assert_type(res_cc.no_store, bool)
assert_type(shared_cc.no_store, bool)
assert_type(any_cc.no_store, bool)

assert_type(req_cc.no_transform, bool)
assert_type(res_cc.no_transform, bool)
assert_type(shared_cc.no_transform, bool)
assert_type(any_cc.no_transform, bool)

req_cc.must_revalidate  # type: ignore
assert_type(res_cc.must_revalidate, bool)
shared_cc.must_revalidate  # type: ignore
assert_type(any_cc.must_revalidate, bool)

req_cc.proxy_revalidate  # type: ignore
assert_type(res_cc.proxy_revalidate, bool)
shared_cc.proxy_revalidate  # type: ignore
assert_type(any_cc.proxy_revalidate, bool)

# NOTE: pyright gets confused about the `Literal[-1]` the types match
assert_type(req_cc.max_age, Union[int, Literal[-1], None])  # pyright: ignore
assert_type(res_cc.max_age, Union[int, Literal[-1], None])  # pyright: ignore
assert_type(shared_cc.max_age, Union[int, Literal[-1], None])  # pyright: ignore
assert_type(any_cc.max_age, Union[int, Literal[-1], None])  # pyright: ignore

req_cc.s_maxage  # type: ignore
assert_type(res_cc.s_maxage, Union[int, None])
shared_cc.s_maxage  # type: ignore
assert_type(any_cc.s_maxage, Union[int, None])

req_cc.s_max_age  # type: ignore
assert_type(res_cc.s_max_age, Union[int, None])
shared_cc.s_max_age  # type: ignore
assert_type(any_cc.s_max_age, Union[int, None])

req_cc.stale_while_revalidate  # type: ignore
assert_type(res_cc.stale_while_revalidate, Union[int, None])
shared_cc.stale_while_revalidate  # type: ignore
assert_type(any_cc.stale_while_revalidate, Union[int, None])

req_cc.stale_if_error  # type: ignore
assert_type(res_cc.stale_if_error, Union[int, None])
shared_cc.stale_if_error  # type: ignore
assert_type(any_cc.stale_if_error, Union[int, None])
