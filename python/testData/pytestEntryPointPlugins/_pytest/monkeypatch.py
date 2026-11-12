from _pytest.fixtures import fixture


class MonkeyPatch:
    pass


@fixture
def monkeypatch() -> MonkeyPatch:
    return MonkeyPatch()
