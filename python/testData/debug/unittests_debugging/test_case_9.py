import pytest


class TestClass:
    @pytest.mark.skip
    def test_add_1(self):
        assert 100 + 200 == 400, "failed"

    @pytest.mark.skip
    def test_add_2(self):
        assert 100 + 200 == 300, "failed"

    @pytest.mark.xfail
    def test_add_3(self):
        assert 15 + 13 == 28, "failed"

    @pytest.mark.xfail
    def test_add_4(self):
        assert 15 + 13 == 100, "failed"
