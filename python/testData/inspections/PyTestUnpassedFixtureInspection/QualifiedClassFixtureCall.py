import pytest


class TestBla:
    @pytest.fixture
    def my_f(self):
        ...

    def test_(self):
        self.my_f()  # no inspection warning expected
