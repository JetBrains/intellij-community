#!/usr/bin/env python3
from __future__ import annotations

import os
import sys

from parse_metadata import read_dependencies

distributions = sys.argv[1:]
if not distributions:
    distributions = os.listdir("stubs")

requirements = set[str]()
for distribution in distributions:
    requirements.update(read_dependencies(distribution).external_pkgs)

for requirement in sorted(requirements):
    print(requirement)
