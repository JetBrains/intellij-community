#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import pytest


@pytest.fixture
def number_of_things():
    return 1


@pytest.fixture
def actual_fixture(number_of_things):
    return range(number_of_things)


@pytest.mark.parametrize("number_of_things", [2])
def test_something_else(actual_fixture):
    pass
