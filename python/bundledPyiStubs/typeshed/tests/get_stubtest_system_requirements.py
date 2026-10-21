#!/usr/bin/env python3
import sys

from ts_utils.requirements import get_stubtest_system_requirements

if __name__ == "__main__":
    distributions = sys.argv[1:]
    for requirement in sorted(get_stubtest_system_requirements(distributions)):
        print(requirement)
