from __future__ import annotations

from typing import TYPE_CHECKING

import pytest

if TYPE_CHECKING:
    import django.test


@pytest.fixture()
def client() -> django.test.Client:
    from django.test.client import Client

    return Client()


@pytest.fixture()
def rf() -> django.test.RequestFactory:
    from django.test.client import RequestFactory

    return RequestFactory()
