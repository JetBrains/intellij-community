from pathlib import Path

import pytest


@pytest.fixture
def community_python_root():
    yield Path(__file__).parent.parent.parent


@pytest.fixture
def helpers_root(community_python_root):
    yield community_python_root / "helpers"


@pytest.fixture
def helpers_test_resources_root(community_python_root):
    yield community_python_root / "helpersTestResources"
