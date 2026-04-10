#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# pytest-mock plugin stub for testing. It follows the structure of pytest_mock/plugin.py.
import pytest
from unittest.mock import DEFAULT, MagicMock
from typing import Any, Generator


class MockerFixture:
    """Stub for pytest_mock.plugin.MockerFixture"""

    def __init__(self, config: Any) -> None:
        self.patch = self._Patcher()

    class _Patcher:
        """The type of `mocker.patch`: it is callable and has the `object`, `dict` and `multiple` methods."""

        def object(self, target, attribute, new=DEFAULT, spec=None, create=False, spec_set=None,
                   autospec=None, new_callable=None, **kwargs) -> MagicMock:
            pass

        def multiple(self, target, spec=None, create=False, spec_set=None, autospec=None,
                     new_callable=None, **kwargs) -> dict[str, MagicMock]:
            pass

        def dict(self, in_dict, values=(), clear=False, **kwargs) -> Any:
            pass

        def __call__(self, target, new=DEFAULT, spec=None, create=False, spec_set=None,
                     autospec=None, new_callable=None, **kwargs) -> MagicMock:
            pass

    def stopall(self) -> None:
        """Stop all active patches."""
        pass

    def spy(self, obj, name):
        """Create a spy on an object's method."""
        pass

    def stub(self, name=None):
        """Create a stub."""
        pass


def _mocker(pytestconfig: Any) -> Generator[MockerFixture, None, None]:
    """
    Return an object that has the same interface to the `mock` module, but
    takes care of automatically undoing all patches after each test method.
    """
    result = MockerFixture(pytestconfig)
    yield result
    result.stopall()


mocker = pytest.fixture()(_mocker)  # default scope is function
class_mocker = pytest.fixture(scope="class")(_mocker)
module_mocker = pytest.fixture(scope="module")(_mocker)
package_mocker = pytest.fixture(scope="package")(_mocker)
session_mocker = pytest.fixture(scope="session")(_mocker)
