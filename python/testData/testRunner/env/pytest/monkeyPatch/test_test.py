
import time

import pytest


@pytest.fixture()
def patch_time_strftime(monkeypatch):
    def mockreturn(*args):
        return '2016-7-25_8-54-30'
    monkeypatch.setattr(time, 'strftime', mockreturn)


def test_monkeypatch(patch_time_strftime):
    assert time.strftime('%Y-%m-%d_%H-%M-%S') == '2016-7-25_8-54-30'