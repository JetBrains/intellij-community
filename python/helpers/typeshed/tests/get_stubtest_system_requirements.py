#!/usr/bin/env python3
import os
import sys

from parse_metadata import read_stubtest_settings

platform = sys.platform
distributions = sys.argv[1:]
if not distributions:
    distributions = os.listdir("stubs")

for distribution in distributions:
    stubtest_settings = read_stubtest_settings(distribution)
    for package in stubtest_settings.system_requirements_for_platform(platform):
        print(package)
