import pytest

from .fixtures import (
    client,  # noqa: F401
    rf,  # noqa: F401
)


def pytest_load_initial_conftests(early_config, parser, args) -> None:
    early_config.addinivalue_line(
        "markers",
        "django_db(transaction=False, reset_sequences=False, databases=None, "
        "serialized_rollback=False, available_apps=None): "
        "Mark the test as using the Django test database.",
    )
    early_config.addinivalue_line("markers", "urls(modstr): Use a different URLconf for this test.")


@pytest.fixture(scope="session")
def django_db_modify_db_settings() -> None:
    pass
