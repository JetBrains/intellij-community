from typing_extensions import assert_type

from networkx.utils.backends import _dispatchable


@_dispatchable
def some_method(int_p: int, str_p: str) -> float:
    return 0.0


# Wrong param / order
some_method("", 0)  # type: ignore
# backend is kw-only
some_method(0, "", None)  # type: ignore
# No backend means no **backend_kwargs allowed
some_method(0, "", backend_specific_kwarg="")  # type: ignore
some_method(0, "", backend=None, backend_specific_kwarg="")  # type: ignore

# Correct usage
assert_type(some_method(0, ""), float)
# type system doesn't allow this yet (see comment in networkx/utils/backends.pyi)
# assert_type(some_method(0, "", backend=None), float)
assert_type(some_method(0, "", backend="custom backend", backend_specific_kwarg=""), float)
