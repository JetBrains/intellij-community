"""
Tests the warnings.deprecated function.
"""

# pyright: reportDeprecated=true

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#deprecated
# See also https://peps.python.org/pep-0702/

from typing import Protocol, override
from typing_extensions import deprecated

# > Type checkers should produce a diagnostic whenever they encounter a usage of an object
# > marked as deprecated. [...] For deprecated classes and functions, this includes:

# > * `from` imports

from _directives_deprecated_library import Ham  # E: Use of deprecated class Ham
import _directives_deprecated_library as library


# > * References through module, class, or instance attributes

library.norwegian_blue(1)  # E: Use of deprecated function norwegian_blue
map(library.norwegian_blue, [1, 2, 3])  # E: Use of deprecated function norwegian_blue


# > For deprecated overloads, this includes all calls that resolve to the deprecated overload.

library.foo(1)  # E: Use of deprecated overload for foo
library.foo("x")  # OK


ham = Ham()  # E?: OK (already reported above)


# > * Any syntax that indirectly triggers a call to the function.

spam = library.Spam()

_ = spam + 1  # E: Use of deprecated method Spam.__add__
spam += 1  # E: Use of deprecated method Spam.__add__

spam.greasy  # E: Use of deprecated property Spam.greasy
spam.shape  # OK

spam.shape = "cube"  # E: Use of deprecated property setter Spam.shape
spam.shape += "cube"  # E: Use of deprecated property setter Spam.shape


class Invocable:
    @deprecated("Deprecated")
    def __call__(self) -> None:
        ...


invocable = Invocable()
invocable()  # E: Use of deprecated method __call__


# > * Any usage of deprecated objects in their defining module


@deprecated("Deprecated")
def lorem() -> None:
    ...


lorem()  # E: Use of deprecated function lorem


# > There are additional scenarios where deprecations could come into play.
# > For example, an object may implement a `typing.Protocol`,
# > but one of the methods required for protocol compliance is deprecated.
# > As scenarios such as this one appear complex and relatively unlikely to come up in practice,
# > this PEP does not mandate that type checkers detect them.


class SupportsFoo1(Protocol):
    @deprecated("Deprecated")
    def foo(self) -> None:
        ...

    def bar(self) -> None:
        ...


class FooConcrete1(SupportsFoo1):
    @override
    def foo(self) -> None:  # E?: Implementation of deprecated method foo
        ...

    def bar(self) -> None:
        ...


def foo_it(f: SupportsFoo1) -> None:
    f.foo()  # E: Use of deprecated method foo
    f.bar()


class SupportsFoo2(Protocol):
    def foo(self) -> None:
        ...


class FooConcrete2:
    @deprecated("Deprecated")
    def foo(self) -> None:
        ...


def takes_foo(f: SupportsFoo2) -> None:
    ...


def caller(c: FooConcrete2) -> None:
    takes_foo(
        c
    )  # E?: FooConcrete2 is a SupportsFoo2, but only because of a deprecated method
