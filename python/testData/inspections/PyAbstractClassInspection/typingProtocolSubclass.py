from typing import Protocol
from abc import abstractmethod


# ------------------
class MP1(Protocol):
    name: str


class CMP1(MP1):  # ok
    pass


def test_mp1(mp1: MP1):
    pass


test_mp1(CMP1())


# ------------------
class MP2(Protocol):
    name: str = "name"


class CMP2(MP2):  # ok
    pass


def test_mp2(mp2: MP2):
    pass


test_mp2(CMP2())


# ------------------
class MP3(Protocol):
    def foo(self) -> int:
        return 42


class CMP3(MP3):  # ok
    pass


def test_mp3(mp3: MP3):
    pass


test_mp3(CMP3())


# ------------------
class MP4(Protocol):
    def foo(self) -> int:
        pass


class CMP4(MP4):  # ok
    pass


def test_mp4(mp4: MP4):
    pass


test_mp4(CMP4())


# ------------------
class MP5(Protocol):
    @abstractmethod
    def foo(self) -> int:
        raise NotImplementedError


class <weak_warning descr="Class CMP5 must implement all abstract methods">CMP5</weak_warning>(MP5):  # fail
    pass


def test_mp5(mp5: MP5):
    pass


test_mp5(CMP5())


# ------------------
class MP6(Protocol):
    @abstractmethod
    def foo(self) -> int:
        raise NotImplementedError


class CMP6(MP6):  # ok
    @abstractmethod
    def bar(self) -> int:
        raise NotImplementedError


def test_mp6(mp6: MP6):
    pass


test_mp6(CMP6())


# ------------------
class MP7(Protocol):
    @abstractmethod
    def foo(self) -> int:
        raise NotImplementedError


class PMP7(MP7, Protocol):  # ok
    def bar(self) -> int:
        pass