#!/usr/bin/env python3

# TODO: It should be possible to specify the Python version and platform
# and limit the output to the packages that are compatible with that version
# and platform.
import sys

from ts_utils.requirements import get_external_stub_requirements

if __name__ == "__main__":
    distributions = sys.argv[1:]
    for requirement in sorted(get_external_stub_requirements(distributions), key=str):
        print(requirement)
