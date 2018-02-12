# Stubs for collections.abc (introduced from Python 3.3)
#
# https://docs.python.org/3.3/whatsnew/3.3.html#collections
import sys

if sys.version_info >= (3, 3):
    from . import (
        Container as Container,
        Hashable as Hashable,
        Iterable as Iterable,
        Iterator as Iterator,
        Sized as Sized,
        Callable as Callable,
        Mapping as Mapping,
        MutableMapping as MutableMapping,
        Sequence as Sequence,
        MutableSequence as MutableSequence,
        Set as Set,
        MutableSet as MutableSet,
        MappingView as MappingView,
        ItemsView as ItemsView,
        KeysView as KeysView,
        ValuesView as ValuesView,
    )

if sys.version_info >= (3, 5):
    from . import (
        Generator as Generator,
        ByteString as ByteString,
        Awaitable as Awaitable,
        Coroutine as Coroutine,
        AsyncIterable as AsyncIterable,
        AsyncIterator as AsyncIterator,
    )

if sys.version_info >= (3, 6):
    from . import (
        Collection as Collection,
        Reversible as Reversible,
        AsyncGenerator as AsyncGenerator,
    )
