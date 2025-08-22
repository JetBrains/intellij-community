from __future__ import annotations

import itertools
import os
import sys
from collections.abc import Iterable

from packaging.requirements import Requirement

from ts_utils.metadata import read_dependencies, read_stubtest_settings
from ts_utils.paths import STUBS_PATH


def get_external_stub_requirements(distributions: Iterable[str] = ()) -> set[Requirement]:
    if not distributions:
        distributions = os.listdir(STUBS_PATH)

    return set(itertools.chain.from_iterable([read_dependencies(distribution).external_pkgs for distribution in distributions]))


def get_stubtest_system_requirements(distributions: Iterable[str] = (), platform: str = sys.platform) -> set[str]:
    if not distributions:
        distributions = os.listdir(STUBS_PATH)

    return set(
        itertools.chain.from_iterable(
            [read_stubtest_settings(distribution).system_requirements_for_platform(platform) for distribution in distributions]
        )
    )
