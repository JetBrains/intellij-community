import pytest
from django.test.client import Client, RequestFactory


@pytest.fixture()
def client() -> Client:
    return Client()


@pytest.fixture()
def rf() -> RequestFactory:
    return RequestFactory()
