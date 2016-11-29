#!/usr/bin/python

import os

from setuptools import setup
from setuptools import find_packages

package = 'python_package_template'
setup_dir = os.path.dirname(os.path.abspath(__file__))

setup(name=package,
      version="!",
      include_package_data=True,
      )
