#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import pytest


@pytest.fixture
def mocker() -> int:
    return 1


def test_overridden_mocker(mocker):
    """The fixture defined in this file overrides the fixture of pytest-mock."""
    pass
