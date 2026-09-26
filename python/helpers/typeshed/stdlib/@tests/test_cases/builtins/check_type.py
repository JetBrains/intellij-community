from typing_extensions import assert_type


class Meta(type): ...


call = Meta.__dict__["__call__"]

# Regression tests for https://github.com/python/typeshed/pull/16299
assert_type(int.__subclasses__(), list[type[int]])
assert_type(str.__subclasses__(), list[type[str]])
assert_type(BaseException.__subclasses__(), list[type[BaseException]])
