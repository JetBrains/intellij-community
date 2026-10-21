from typing_extensions import assert_type

from setuptools import Command as setuptools_Command, Distribution as setuptools_Distribution, setup
from setuptools._distutils.cmd import Command as distutils_Command
from setuptools._distutils.dist import Distribution as distutils_Distribution

# Ensure that any distutils-derived classes are usable w/o type variance issues
assert_type(
    setup(
        cmdclass=dict[str, type[distutils_Command]](),
        command_obj=dict[str, distutils_Command](),
        distclass=distutils_Distribution,
    ),
    distutils_Distribution,
)
assert_type(
    setup(
        cmdclass=dict[str, type[setuptools_Command]](),
        command_obj=dict[str, setuptools_Command](),
        distclass=setuptools_Distribution,
    ),
    setuptools_Distribution,
)
