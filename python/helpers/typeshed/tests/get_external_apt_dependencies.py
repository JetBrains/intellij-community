#!/usr/bin/env python3

import itertools
import os
import sys

from ts_utils.metadata import read_metadata
from ts_utils.paths import STUBS_PATH

if __name__ == "__main__":
    distributions = sys.argv[1:]
    if not distributions:
        distributions = os.listdir(STUBS_PATH)
    dependencies = set(
        itertools.chain.from_iterable(
            read_metadata(distribution).stubtest_settings.apt_dependencies for distribution in distributions
        )
    )
    for dependency in sorted(dependencies):
        print(dependency)
