from typing import Generator, Iterable, Iterator, AsyncIterable, AsyncIterator, AsyncGenerator

# Fix incorrect YieldType
def a() -> Iterable[str]:
    yield <warning descr="Expected yield type 'str', got 'int' instead">42</warning>

def b() -> Iterator[str]:
    yield <warning descr="Expected yield type 'str', got 'int' instead">42</warning>

def c() -> Generator[str, Any, int]:
    yield <warning descr="Expected yield type 'str', got 'int' instead">13</warning>
    return 42

def c() -> Generator[int, Any, str]:
    yield 13
    return <warning descr="Expected type 'str', got 'int' instead">42</warning>

# Suggest AsyncGenerator
async def d() -> <warning descr="Expected type 'AsyncGenerator[int, None]', got 'Iterable[int]' instead">Iterable[int]</warning>:
    yield 42

async def e() -> <warning descr="Expected type 'AsyncGenerator[int, None]', got 'Iterator[int]' instead">Iterator[int]</warning>:
    yield 42

async def f() -> <warning descr="Expected type 'AsyncGenerator[int, str]', got 'Generator[int, str, None]' instead">Generator[int, str, None]</warning>:
    yield 13

# Suggest sync Generator
def g() -> <warning descr="Expected type 'Generator[int, None, None]', got 'AsyncIterable[int]' instead">AsyncIterable[int]</warning>:
    yield 42

def h() -> <warning descr="Expected type 'Generator[int, None, None]', got 'AsyncIterator[int]' instead">AsyncIterator[int]</warning>:
    yield 42

def i() -> <warning descr="Expected type 'Generator[int, str, None]', got 'AsyncGenerator[int, str]' instead">AsyncGenerator[int, str]</warning>:
    yield 13

def j() -> Iterator[int]:
    yield from j()

def k() -> Iterator[str]:
    yield from <warning descr="Expected yield type 'str', got 'int' instead">j()</warning>
    yield from <warning descr="Expected yield type 'str', got 'int' instead">[1]</warning>

def l() -> Generator[None, int, None]:
    x: float = yield

def m() -> Generator[None, float, None]:
    yield from <warning descr="Expected send type 'float', got 'int' instead">l()</warning>
    
def n() -> Generator[None, float, None]:
  x: float = yield

def o() -> Generator[None, int, None]:
    yield from n()

def p() -> Generator[int, None, None]:
    yield from [1, 2]
    yield from [3, 4]
    
def q() -> int:
    x = lambda: (yield "str")
    return 42

async def r() -> AsyncGenerator[int]:
    yield 42
    
def s() -> Generator[int]:
    yield from <warning descr="Cannot yield from 'AsyncGenerator[int, None]', use async for instead"><warning descr="Expected type 'collections.Iterable', got 'AsyncGenerator[int, None]' instead">r()</warning></warning>