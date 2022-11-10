#!/usr/bin/env python3
import os
import sys

import tomli

distributions = sys.argv[1:]
if not distributions:
    distributions = os.listdir("stubs")

for distribution in distributions:
    with open(f"stubs/{distribution}/METADATA.toml", "rb") as file:
        for apt_package in tomli.load(file).get("tool", {}).get("stubtest", {}).get("apt_dependencies", []):
            print(apt_package)
