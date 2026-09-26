from __future__ import annotations

from typing_extensions import assert_type

from docker.models.containers import Container


def check_attach(c: Container) -> None:
    assert_type(c.attach(), bytes)
    assert_type(c.attach(stream=False), bytes)
    for line in c.attach(stream=True):
        assert_type(line, bytes)
